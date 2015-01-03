package ca.mcmaster.capstone.monitoralgorithm;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ca.mcmaster.capstone.initializer.Initializer;
import ca.mcmaster.capstone.networking.CapstoneService;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.NonNull;

/* Class to hold the main algorithm code.*/
// TODO: refactor Service components out into MonitorService to separate from actual Monitor logic
public class Monitor extends Service {

    private static final Map<Integer, Event> history = new HashMap<>();
    private static final Set<Token> waitingTokens = new LinkedHashSet<>();
    private static NetworkPeerIdentifier monitorID = null;
    private static final Set<GlobalView> GV = new HashSet<>();
    private static final int numPeers = 2;
    private static ExecutorService workQueue;
    private static volatile boolean cancelled = false;
    private static Future<?> monitorJob = null;
    private static Future<?> tokenPollJob = null;
    private static Future<?> eventPollJob = null;
    private Intent networkServiceIntent;
    private Intent initializerServiceIntent;
    private static final NetworkServiceConnection networkServiceConnection = new NetworkServiceConnection();
    private static final InitializerServiceConnection initializerServiceConnection = new InitializerServiceConnection();

    @Override
    public IBinder onBind(Intent intent) {
        return new MonitorBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        cancelled = false;
        networkServiceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(networkServiceIntent, networkServiceConnection, BIND_AUTO_CREATE);
        initializerServiceIntent = new Intent(this, Initializer.class);
        getApplicationContext().bindService(initializerServiceIntent, initializerServiceConnection, BIND_AUTO_CREATE);

        workQueue = Executors.newSingleThreadExecutor();

        monitorJob = workQueue.submit(Monitor::monitorLoop);
        Log.d("thread", "Started monitor!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("thread", "Stopped monitor!");
        cancelled = true;
        workQueue.shutdownNow();
        cancelJobs(tokenPollJob, eventPollJob, monitorJob);
        getApplicationContext().unbindService(networkServiceConnection);
        getApplicationContext().unbindService(initializerServiceConnection);
    }

    private static void cancelJobs(@NonNull final Future<?> ... jobs) {
        for (final Future<?> job : jobs) {
            if (job != null) {
                job.cancel(true);
            }
        }
    }

    /*
     * Perform some basic initialization.
     *
     * @param initialStates The initial state of each known process.
     */
    public static void init() {
        Log.d("monitor", "Initializing monitor");
        while (networkServiceConnection.getNetworkLayer() == null && !cancelled) {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                Log.d("monitor", "NetworkLayer connection is not established: " + e.getLocalizedMessage());
            }
        }

        monitorID = initializerServiceConnection.getInitializer().getLocalPID();
        final Map<String, NetworkPeerIdentifier> virtualIdentifiers = initializerServiceConnection.getInitializer().getVirtualIdentifiers();

        Automaton.processAutomatonFile(initializerServiceConnection.getInitializer().getAutomatonFile());
        Automaton.build(virtualIdentifiers.get("x1"), virtualIdentifiers.get("x2"));

        //TODO: Eventually this will be constructed from a text file or something.
        final Valuation<Double> val1 = new Valuation<>(new HashMap<String, Double>() {{
            put("x1", 0.0);
        }});
        final Valuation<Double> val2 = new Valuation<>(new HashMap<String, Double>() {{
            put("x2", 0.0);
        }});

        final VectorClock vec = new VectorClock(new HashMap<NetworkPeerIdentifier, Integer>() {{
            put(virtualIdentifiers.get("x1"), 0);
            put(virtualIdentifiers.get("x2"), 0);
        }});
        final Map<NetworkPeerIdentifier, ProcessState> initialStates = new HashMap<NetworkPeerIdentifier, ProcessState>() {{
            //FIXME: Double use of virtualIdentifiers on each line makes me think there's some possible refactoring here
            put(virtualIdentifiers.get("x1"), new ProcessState(virtualIdentifiers.get("x1"), val1, vec));
            put(virtualIdentifiers.get("x2"), new ProcessState(virtualIdentifiers.get("x2"), val2, vec));
        }};

        final GlobalView initialGV = new GlobalView();
        initialGV.setCurrentState(Automaton.getInitialState());
        initialGV.setStates(initialStates);
        initialGV.setCurrentState(Automaton.advance(initialGV));
        initialGV.setCut(new VectorClock(new HashMap<NetworkPeerIdentifier, Integer>() {{
            put(virtualIdentifiers.get("x1"), 0);
            put(virtualIdentifiers.get("x2"), 0);
        }}));
        GV.add(initialGV);
        Log.d("monitor", "Finished initializing monitor");
    }

    /*
     * The main loop of the algorithm.
     *
     * @param initialStates The initial state of each known process.
     */
    public static void monitorLoop() {
        init();
        Log.d("monitor", "submitting loop tasks");
        tokenPollJob = Executors.newSingleThreadExecutor().submit(Monitor::pollTokens);
        eventPollJob = Executors.newSingleThreadExecutor().submit(Monitor::pollEvents);
    }

    private static void pollTokens() {
        while (!cancelled) {
            Log.d("monitor", "pollTokens looping");
            final Token token = receive();
            if (token != null) {
                workQueue.submit(() -> receiveToken(token));
            }
        }
        Log.d("monitor", "pollTokens exiting");
    }

    private static void pollEvents() {
        while (!cancelled) {
            Log.d("monitor", "pollEvents looping");
            final Event event = read();
            if (event != null) {
                workQueue.submit(() -> receiveEvent(event));
            }
        }
        Log.d("monitor", "pollEvents exiting");
    }

    private static Token receive() {
        Log.d("monitor", "receive entered");
        Token token = null;
        while (token == null && !cancelled) {
            try {
                token = networkServiceConnection.getNetworkLayer().receiveToken();
            } catch (InterruptedException e) {
                Log.d("thread", "Woke up without a token, trying again? " + cancelled + " : " + e.getLocalizedMessage());
            }
        }
        Log.d("monitor", "receive exited");
        return token;
    }

    private static void send(@NonNull final Token token, @NonNull final NetworkPeerIdentifier pid) {
        Log.d("monitor", "sending token");
        networkServiceConnection.getNetworkLayer().sendTokenToPeer(pid, token);
    }

    private static Event read() {
        Log.d("monitor", "read entered");
        Event event = null;
        while (event == null && !cancelled) {
            try {
                event = networkServiceConnection.getNetworkLayer().receiveEvent();
            } catch (InterruptedException e) {
                Log.d("thread", "Woke up without an event, trying again? " + cancelled + " : " + e.getLocalizedMessage());
            }
        }
        Log.d("monitor", "read exited");
        return event;
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
        Log.d("monitor", "Exiting receiveEvent");
    }

    private static Set<GlobalView> mergeSimilarGlobalViews(@NonNull final Collection<GlobalView> gv) {
        Log.d("monitor", "Entering mergeSimilarGlobalViews");
        final Set<GlobalView> merged = new HashSet<>();
        for (GlobalView gv1 : gv) {
            for (GlobalView gv2 : gv) {
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
        gv.updateWithEvent(event);
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
        final Map<NetworkPeerIdentifier, Set<AutomatonTransition>> consult = new HashMap<>();
        for (final AutomatonTransition trans : Automaton.getTransitions()) {
            final AutomatonState current = gv.getCurrentState();
            if (trans.getFrom().equals(current) && !trans.getTo().equals(current)) {
                final Set<NetworkPeerIdentifier> participating = trans.getParticipatingProcesses();
                final Set<NetworkPeerIdentifier> forbidding = trans.getForbiddingProcesses(gv);
                if (!forbidding.contains(monitorID)) {
                    final Set<NetworkPeerIdentifier> inconsistent = gv.getInconsistentProcesses();
                    // intersection
                    participating.retainAll(inconsistent);
                    // union
                    forbidding.addAll(participating);
                    for (final NetworkPeerIdentifier process : forbidding) {
                        gv.getPendingTransitions().add(trans);
                        if (consult.get(process) == null) {
                            consult.put(process, new HashSet<>());
                        }
                        consult.get(process).add(trans);
                    }
                }
            }
        }

        for (final Map.Entry<NetworkPeerIdentifier, Set<AutomatonTransition>> entry : consult.entrySet()) {
            // Get all the conjuncts for process j
            final Set<Conjunct> conjuncts = new HashSet<>();
            for (final AutomatonTransition trans : entry.getValue()) {
                conjuncts.addAll(trans.getConjuncts());
            }
            //Build map to add to token
            final Map<Conjunct, Conjunct.Evaluation> forToken = new HashMap<>();
            for (final Conjunct conjunct : conjuncts) {
                forToken.put(conjunct, Conjunct.Evaluation.NONE);
            }
            final Token token = new Token.Builder(monitorID, entry.getKey()).targetEventId(gv.getCut().process(entry.getKey()) + 1)
                    .cut(event.getVC()).conjuncts(forToken).automatonTransitions(entry.getValue())
                    .build();
            gv.getTokens().add(token);
        }
        final Token token = gv.getTokenWithMostConjuncts();
        //XXX: This may not be correct. More investigation required.
        if (token != null) {
            Log.d("monitor", "Sending a nice shiny token!");
            send(token, token.getDestination());
            token.setSent(true);
        }
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
                globalView.updateWithToken(token);
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
                            globalView.updateWithToken(tok);
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
                waitingTokens.add(token);
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
        final VectorClock.Comparison comp = event.getVC().compareToClock(token.getCut());
        if (comp == VectorClock.Comparison.CONCURRENT || comp == VectorClock.Comparison.EQUAL) {
            evaluateToken(token, event);
        } else if (comp == VectorClock.Comparison.BIGGER) {
            Log.d("monitor", "Waiting for next event");
            waitingTokens.add(token.waitForNextEvent());
        } else {
            final Map<Conjunct, Conjunct.Evaluation> conjunctsMap = token.getConjunctsMap();
            for (final Conjunct conjunct : conjunctsMap.keySet()) {
                conjunctsMap.put(conjunct, Conjunct.Evaluation.FALSE);
            }
            final Event eventPrime = history.get(token.getTargetEventId());
            final Token newToken = new Token.Builder(token).cut(eventPrime.getVC()).conjuncts(conjunctsMap).targetProcessState(eventPrime.getState()).build();
            Log.d("monitor", "Sending a token back home.");
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
        Log.d("monitor", "Entering evaluateToken");
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
