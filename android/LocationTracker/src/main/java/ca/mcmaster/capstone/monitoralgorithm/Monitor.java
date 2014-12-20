package ca.mcmaster.capstone.monitoralgorithm;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import ca.mcmaster.capstone.networking.CapstoneService;
import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;
import lombok.NonNull;
import lombok.Synchronized;

/* Class to hold the main algorithm code.*/
// TODO: refactor Service components out into MonitorService to separate from actual Monitor logic
public class Monitor extends Service {

    private static class Receiver<T> implements Runnable {
        /**
         * Should be a blocking call, else the Thread running this Receiver will spend a lot of time spinning
         */
        @NonNull private final Callable<T> callable;
        private final Queue<T> receiveQueue = new ConcurrentLinkedQueue<>();
        private volatile boolean run = true;

        public Receiver(@NonNull final Callable<T> callable) {
            this.callable = callable;
        }

        @Override
        public void run() {
            while (run) {
                try {
                    receiveQueue.add(callable.call());
                } catch (final Exception e) {
                    Log.v("MonitorService", "Receiver helper thread failed to call callable: " + e.getLocalizedMessage());
                }
            }
        }

        public T receive() {
            return receiveQueue.poll();
        }

        public void stop() {
            run = false;
        }
    }

    private static final Map<Integer, Event> history = new HashMap<>();
    private static final Set<Token> waitingTokens = new LinkedHashSet<>();
    private static HashableNsdServiceInfo monitorID = null;
    private static final Set<GlobalView> GV = new HashSet<>();
    private static final int numPeers = 1;
    private static volatile boolean runMonitor = true;
    private Thread thread;
    private Intent networkServiceIntent;
    private static final NetworkServiceConnection serviceConnection = new NetworkServiceConnection();

    @Override
    public IBinder onBind(Intent intent) {
        return new MonitorBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        networkServiceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(networkServiceIntent, serviceConnection, BIND_AUTO_CREATE);
        runMonitor = true;

        thread = new Thread(Monitor::monitorLoop);
        Log.d("thread", "Started monitor!");
        thread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("thread", "Stopped monitor!");
        runMonitor = false;
        getApplicationContext().unbindService(serviceConnection);
    }

    private static Token receive() {
        while (serviceConnection.getNetworkLayer() == null) {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                Log.d("thread", "NetworkLayer connection is not established: " + e.getLocalizedMessage());
            }
        }
        Token token = null;
        while (token == null) {
            try {
                token = serviceConnection.getNetworkLayer().receiveToken();
            } catch (InterruptedException e) {
                Log.d("thread", "Woke up without a token, trying again: " + e.getLocalizedMessage());
            }
        }
        return token;
    }

    private static void send(@NonNull final Token token, @NonNull final HashableNsdServiceInfo pid) {
        while (serviceConnection.getNetworkLayer() == null) {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                Log.d("thread", "NetworkLayer connection is not established: " + e.getLocalizedMessage());
            }
        }
        serviceConnection.getNetworkLayer().sendTokenToPeer(pid, token);
    }

    private static Event read() {
        while (serviceConnection.getNetworkLayer() == null) {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                Log.d("thread", "NetworkLayer connection is not established: " + e.getLocalizedMessage());
            }
        }
        Event event = null;
        while (event == null) {
            try {
                event = serviceConnection.getNetworkLayer().receiveEvent();
            } catch (InterruptedException e) {
                Log.d("thread", "Woke up without an event, trying again: " + e.getLocalizedMessage());
            }
        }
        return event;
    }

    /*
     * Perform some basic initialization.
     *
     * @param initialStates The initial state of each known process.
     */
    public static void init() {
        Log.d("monitor", "Initializing monitor");
        while (serviceConnection.getNetworkLayer() == null) {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                Log.d("monitor", "NetworkLayer connection is not established: " + e.getLocalizedMessage());
            }
        }
        monitorID = HashableNsdServiceInfo.get(serviceConnection.getNetworkLayer().getLocalNsdServiceInfo());

        final Map<String, HashableNsdServiceInfo> virtualIdentifiers = generateVirtualIdentifiers();
        Automaton.build(virtualIdentifiers.get("x1"), virtualIdentifiers.get("x2"));

        //TODO: Eventually this will be constructed from a text file or something.
        final Valuation val = new Valuation(new HashMap<String, Double>() {{
            put("x1", 0.0);
            put("x2", 0.0);
        }}); // FIXME: should be parametrized?

        final VectorClock vec = new VectorClock(new HashMap<HashableNsdServiceInfo, Integer>() {{
            put(monitorID, 0);
        }});
        final Map<HashableNsdServiceInfo, ProcessState> initialStates = new HashMap<HashableNsdServiceInfo, ProcessState>() {{
            put(monitorID, new ProcessState(monitorID, val, vec));
        }};

        final GlobalView initialGV = new GlobalView();
        initialGV.setCurrentState(Automaton.getInitialState());
        initialGV.setStates(initialStates);
        initialGV.setCurrentState(Automaton.advance(initialGV));
        initialGV.setCut(new VectorClock(new HashMap<HashableNsdServiceInfo, Integer>() {{ put(monitorID, 0); }}));
        GV.add(initialGV);
        Log.d("monitor", "Finished initializing monitor");
    }

    //FIXME: This method requires certain elements of global state to be initialized before it is called.
    private static Map<String, HashableNsdServiceInfo> generateVirtualIdentifiers() {
        final Map<String, HashableNsdServiceInfo> virtualIdentifiers = new HashMap<>();
        while (true) {
            if (serviceConnection.getNetworkLayer().getNsdPeers().size() == numPeers) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                //Don't care
            }
        }
        final List<HashableNsdServiceInfo> sortedIdentifiers = new ArrayList<HashableNsdServiceInfo>() {{
            add(monitorID);
            addAll(serviceConnection.getNetworkLayer().getNsdPeers());
        }};
        Collections.sort(sortedIdentifiers, (f, s) -> Integer.compare(f.hashCode(), s.hashCode()));
        for (final HashableNsdServiceInfo hashableNsdServiceInfo : sortedIdentifiers) {
            final String virtualIdentifier = "x" + (sortedIdentifiers.indexOf(hashableNsdServiceInfo) + 1);
            virtualIdentifiers.put(virtualIdentifier, hashableNsdServiceInfo);
        }
        return virtualIdentifiers;
    }

    /*
     * The main loop of the algorithm.
     *
     * @param initialStates The initial state of each known process.
     */
    public static void monitorLoop() {
        init();

        final Receiver<Token> tokenReceiver = new Receiver<>(Monitor::receive);
        final Thread tokens = new Thread(tokenReceiver);
        tokens.start();
        final Receiver<Event> eventReceiver = new Receiver<>(Monitor::read);
        final Thread events = new Thread(eventReceiver);
        events.start();
        Log.d("monitor", "Starting main loop.");
        while (runMonitor) {
            try {
                Thread.sleep(500); // FIXME: should refactor this to use an ExecutorService or something in the future. Good enough for now...
            } catch (final InterruptedException ie) {
                // don't care
            }
            final Token receivedToken = tokenReceiver.receive();
            if (receivedToken != null) {
                receiveToken(receivedToken);
            }
            Log.d("monitor", "Tried to receive event without blocking");
            final Event localEvent = eventReceiver.receive();
            if (localEvent != null) {
                receiveEvent(localEvent);
            }
            Log.d("monitor", "Tried to receive event without blocking");
        }
        tokenReceiver.stop();
        eventReceiver.stop();
        Log.d("monitor", "Left monitor loop.");
    }

    /*
     * Process local events for global views who have no pending events waiting to be processed.
     *
     * @param event The event to be processed.
     */
    public static void receiveEvent(@NonNull final Event event) {
        Log.d("monitor", "Entering receiveEvent");
        history.put(event.getEid(), event);
        for (final Iterator<Token> i = waitingTokens.iterator(); i.hasNext();) {
            final Token t = i.next();
            if (t.getTargetEventId() == event.getEid()) {
                processToken(t, event);
                i.remove();
            }
        }
        final Set<GlobalView> copyGV = new HashSet<>(GV);
        GV.clear();
        GV.addAll(mergeSimilarGlobalViews(copyGV));
        for (final GlobalView gv : GV) {
            gv.getPendingEvents().add(event);
            if (gv.getTokens().isEmpty()) {
                processEvent(gv, gv.getPendingEvents().remove());
            }
        }
    }

    private static Set<GlobalView> mergeSimilarGlobalViews(@NonNull final Collection<GlobalView> gv) {
        Log.d("monitor", "Entering mergeSimilarGlobalViews");
        final Iterator<GlobalView> it1 = gv.iterator();
        final Iterator<GlobalView> it2 = gv.iterator();
        final Set<GlobalView> merged = new HashSet<>();
        while (it1.hasNext()) {
            final GlobalView gv1 = it1.next();
            while (it2.hasNext()) {
                final GlobalView gv2 = it2.next();
                if (!gv1.equals(gv2)) {
                    final GlobalView newGV = gv1.merge(gv2);
                    if (newGV != null) {
                        merged.add(newGV);
                    }
                }
            }
            merged.add(gv1);
        }
        return merged;
    }

    /*
     * Compute the next state of the monitor automaton. Depending on the information needed to
     * evaluate the transitions of the monitor automaton this may be done locally or there may be a
     * need to consult with another process.
     *
     * @param gv The global view to compute the next monitor state for.
     * @param event The event to be evaluated.
     */
    public static void processEvent(@NonNull final GlobalView gv, @NonNull final Event event) {
        Log.d("monitor", "Entering processEvent");
        gv.setCut(gv.getCut().merge(event.getVC()));
        final ProcessState state = gv.getStates().get(monitorID);
        gv.getStates().put(monitorID, state.update(event));
        if (gv.isConsistent()) {
            gv.setCurrentState(Automaton.advance(gv));
            if (gv.getCurrentState().getStateType() == Automaton.Evaluation.SATISFIED) {
                Log.d("processEvent", "I am satisfied!");
            } else if (gv.getCurrentState().getStateType() == Automaton.Evaluation.VIOLATED) {
                Log.d("processEvent", "I feel violated!");
            }
        }
        checkOutgoingTransitions(gv, event);
    }

    /*
     * Identifies events in gv that are concurrent with event that can enable out going transitions
     * from the current state of the monitor automaton.
     *
     * @param gv The global view to use for finding concurrent events.
     * @param event The event to find concurrent events for.
     */
    private static void checkOutgoingTransitions(@NonNull final GlobalView gv, @NonNull final Event event) {
        Log.d("monitor", "Entering checkOutgoingTransitions");
        final Map<HashableNsdServiceInfo, Set<AutomatonTransition>> consult = new HashMap<>();
        for (final AutomatonTransition trans : Automaton.getTransitions()) {
            final AutomatonState current = gv.getCurrentState();
            Log.d("monitor", current.toString());
            Log.d("monitor", trans.toString());
            if (trans.getFrom().equals(current) && !trans.getTo().equals(current)) {
                final Set<HashableNsdServiceInfo> participating = trans.getParticipatingProcesses();
                final Set<HashableNsdServiceInfo> forbidding = trans.getForbiddingProcesses(gv);
                if (!forbidding.contains(monitorID)) {
                    final Set<HashableNsdServiceInfo> inconsistent = gv.getInconsistentProcesses();
                    // intersection
                    participating.retainAll(inconsistent);
                    // union
                    forbidding.addAll(participating);
                    for (final HashableNsdServiceInfo process : forbidding) {
                        gv.getPendingTransitions().add(trans);
                        if (consult.get(process) == null) {
                            consult.put(process, new HashSet<>());
                        }
                        consult.get(process).add(trans);
                    }
                }
            }
        }

        for (final Map.Entry<HashableNsdServiceInfo, Set<AutomatonTransition>> entry : consult.entrySet()) {
            Log.d("monitor", "Got here 5");
            // Get all the conjuncts for process j
            final Set<Conjunct> conjuncts = new HashSet<>();
            for (final AutomatonTransition trans : entry.getValue()) {
                Log.d("monitor", "Got here 6");
                conjuncts.addAll(trans.getConjuncts());
            }
            //Build map to add to token
            final Map<Conjunct, Conjunct.Evaluation> forToken = new HashMap<>();
            for (final Conjunct conjunct : conjuncts) {
                Log.d("monitor", "Got here 7");
                forToken.put(conjunct, Conjunct.Evaluation.NONE);
            }
            final Token token = new Token.Builder(monitorID, entry.getKey()).targetEventId(gv.getCut().process(entry.getKey()) + 1)
                    .cut(event.getVC()).conjuncts(forToken).automatonTransitions(entry.getValue())
                    .build();
            gv.getTokens().add(token);
        }
        final Token token = gv.getTokenWithMostConjuncts();
        send(token, token.getDestination());
        token.setSent(true);
    }

    /*
     * The method does two things:
     *     1) enable or disable automaton transitions based on the received token
     *     2) evaluate the transition that the received token is requesting
     *
     * @param token The token being received.
     */
    public static void receiveToken(@NonNull final Token token) {
        Log.d("monitor", "Entering receiveToken");
        if (token.getOwner() == monitorID) {
            final List<GlobalView> globalViews = getGlobalView(token);
            for (final GlobalView globalView : globalViews) {
                globalView.update(token);
                for (final AutomatonTransition trans : token.getAutomatonTransitions()) {
                    // Get other tokens for same transition
                    final List<Token> tokens = globalView.getTokensForTransition(trans);
                    boolean enabled = false;
                    try {
                        enabled = trans.enabled(tokens);
                    } catch (Exception e) {
                        Log.d("enabled", "Exceptions while checking that transition is enabled: " + e.getLocalizedMessage());
                    }
                    if (enabled && consistent(globalView, tokens)) {
                        for (final Token tok : tokens) {
                            globalView.update(tok);
                        }
                        final GlobalView gvn1 = new GlobalView(globalView);
                        final GlobalView gvn2 = new GlobalView(globalView);
                        gvn1.setCurrentState(trans.getTo());
                        gvn2.setCurrentState(trans.getTo());
                        gvn1.setTokens(new ArrayList<>());
                        gvn2.setTokens(new ArrayList<>());
                        globalView.getPendingTransitions().remove(trans);
                        GV.add(gvn1);
                        GV.add(gvn2);
                        if (gvn1.getCurrentState().getStateType() == Automaton.Evaluation.SATISFIED) {
                            Log.d("processEvent", "I am satisfied!");
                        } else if (gvn1.getCurrentState().getStateType() == Automaton.Evaluation.VIOLATED) {
                            Log.d("processEvent", "I feel violated!");
                        }
                        processEvent(gvn1, gvn1.getPendingEvents().remove());
                        processEvent(gvn2, history.get(gvn2.getCut().process(monitorID)));
                    } else if (trans.evaluate(globalView.getStates().values()) == Conjunct.Evaluation.FALSE) {
                        globalView.getPendingTransitions().remove(trans);
                    }
                }
                if (globalView.getPendingTransitions().isEmpty()) {
                    boolean hasEnabled = false;
                    for (final AutomatonTransition gvTrans : globalView.getPendingTransitions()) {
                        if (gvTrans.evaluate(globalView.getStates().values()) == Conjunct.Evaluation.TRUE) {
                            GV.removeAll(globalViews);
                            hasEnabled = true;
                            break;
                        }
                    }
                    if (!hasEnabled) {
                        globalView.setTokens(new ArrayList<>());
                        processEvent(globalView, globalView.getPendingEvents().remove());
                    }
                }
                final Token maxConjuncts = globalView.getTokenWithMostConjuncts();
                send(maxConjuncts, maxConjuncts.getDestination());
                maxConjuncts.setSent(true);
            }
        } else {
            boolean hasTarget = false;
            for (final Event event : history.values()) {
                if (event.getEid() == token.getTargetEventId()) {
                    processToken(token, event);
                    hasTarget = true;
                    break;
                }
            }
            if (!hasTarget) {
                waitingTokens.remove(token);
            }
        }
    }

    /*
     * Checks that the vector clock in each token is consistent with gv's vector clock. Consistent
     * means that the clocks are either equal or concurrent.
     *
     * @param gv The GlobalView whose vector clock will be compared.
     * @param tokens The tokens whose vector clocks to compare.
     * @return True if all tokens' vector clocks are consistent with gv's. False otherwise.
     */
    private static boolean consistent(@NonNull final GlobalView gv, @NonNull final List<Token> tokens) {
        final VectorClock viewCut = gv.getCut();
        boolean consistent = true;
        for (final Token token : tokens) {
            final VectorClock tokenClock = token.getCut();
            consistent &= (tokenClock.compareToClock(viewCut) == VectorClock.Comparison.CONCURRENT ||
                    tokenClock.compareToClock(viewCut) == VectorClock.Comparison.EQUAL);
        }
        return consistent;
    }

    /*
     * Decide whether token should be returned to its owner. Token is updated with event.
     *
     * @param token The token to process.
     * @param event The event to update token with.
     */
    public static void processToken(@NonNull final Token token, @NonNull final Event event) {
        Log.d("monitor", "Entering processToken");
        if (event.getVC().compareToClock(token.getCut()) == VectorClock.Comparison.CONCURRENT) {
            evaluateToken(token, event);
        } else {
            final Map<Conjunct, Conjunct.Evaluation> conjunctsMap = token.getConjunctsMap();
            for (final Conjunct conjunct : conjunctsMap.keySet()) {
                conjunctsMap.put(conjunct, Conjunct.Evaluation.FALSE);
            }
            final Event eventPrime = history.get(token.getTargetEventId());
            final Token newToken = new Token.Builder(token).cut(eventPrime.getVC()).conjuncts(conjunctsMap).targetProcessState(eventPrime.getState()).build();
            send(newToken, newToken.getOwner());
        }
    }

    /*
     * Evaluates each of token's predicates.
     *
     * @param token The token whose predicates will be evaluated.
     * @param event The event to use to evaluate the token.
     */
    //FIXME: This needs to be refactored
    public static void evaluateToken(@NonNull final Token token, @NonNull final Event event) {
        token.evaluateConjuncts(event);
        if (token.anyConjunctSatisfied()) {
            final Token newToken = new Token.Builder(token).cut(event.getVC()).targetProcessState(event.getState()).build();
            send(newToken, newToken.getOwner());
        } else {
            waitingTokens.add(token.waitForNextEvent());
        }
    }

    /*
     * Returns a list of global views that contain a copy of token.
     *
     * @param token The token to search for.
     * @return A list of GlobalViews that have a copy of token.
     */
    private static List<GlobalView> getGlobalView(@NonNull final Token token) {
        final List<GlobalView> ret = new ArrayList<>();
        for (final GlobalView gv : GV) {
            for (final Token t : gv.getTokens()) {
                if (token.getOwner() == t.getOwner()) {
                    ret.add(gv);
                    break;
                }
            }
        }
        return ret;
    }
}
