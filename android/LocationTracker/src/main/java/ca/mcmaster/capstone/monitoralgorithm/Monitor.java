package ca.mcmaster.capstone.monitoralgorithm;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

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

import ca.mcmaster.capstone.initializer.InitialState;
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
    private static ExecutorService workQueue;
    private static volatile boolean cancelled = false;
    private static Future<?> monitorJob = null;
    private static Future<?> tokenPollJob = null;
    private static Future<?> eventPollJob = null;
    private Intent networkServiceIntent;
    private Intent initializerServiceIntent;
    private static final NetworkServiceConnection networkServiceConnection = new NetworkServiceConnection();
    private static final InitializerServiceConnection initializerServiceConnection = new InitializerServiceConnection();
    private static final Automaton automaton = Automaton.INSTANCE;

    /* Class to abstract the bulk sending of tokens. */
    private static class TokenSender {
        private static final List<Token> tokensToSendOut = new ArrayList<>();
        private static final List<Token> tokensToSendHome = new ArrayList<>();

        private static void bulkTokenSendOut(final List<Token> tokens) {
            tokensToSendOut.addAll(tokens);
        }

        private static void sendTokenOut(final Token token) {
            tokensToSendOut.add(token);
        }

        private static void sendTokenHome(final Token token) {
            tokensToSendHome.add(token);
        }

        private static void bulkSendTokens() {
            for (Iterator<Token> it = tokensToSendOut.iterator(); it.hasNext(); ) {
                final Token token = it.next();
                send(token, token.getDestination());
                it.remove();
            }
            for (Iterator<Token> it = tokensToSendHome.iterator(); it.hasNext(); ) {
                final Token token = it.next();
                send(token, token.getOwner());
                it.remove();
            }
        }
    }

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

        automaton.processAutomatonFile(initializerServiceConnection.getInitializer().getAutomatonFile(),
                initializerServiceConnection.getInitializer().getConjunctMap(),
                virtualIdentifiers);

        //FIXME: This is pretty messy. We can pobably do better given some time to think.
        List<InitialState.ValuationDummy> valuationDummies = initializerServiceConnection.getInitializer().getInitialState().getValuations();
        Map<NetworkPeerIdentifier, Integer> initialVectorClock = new HashMap<>();
        Map<NetworkPeerIdentifier, Valuation> valuations = new HashMap<>();
        for (InitialState.ValuationDummy valuation : valuationDummies) {
            Map<String, Double> val = new HashMap<>();
            for (InitialState.Variable variable : valuation.getVariables()) {
                val.put(variable.getVariable(), Double.parseDouble(variable.getValue()));
            }
            NetworkPeerIdentifier currentProcess = virtualIdentifiers.get(valuation.getVariables().get(0).getVariable());
            initialVectorClock.put(currentProcess, 0);
            valuations.put(currentProcess, new Valuation<>(val));
        }

        VectorClock vectorClock = new VectorClock(initialVectorClock);
        final Map<NetworkPeerIdentifier, ProcessState> initialStates = new HashMap<>();
        for (Map.Entry<NetworkPeerIdentifier, Valuation> entry : valuations.entrySet()) {
            initialStates.put(entry.getKey(), new ProcessState(entry.getKey(), entry.getValue(), vectorClock));
        }

        final GlobalView initialGV = new GlobalView();
        initialGV.setCurrentState(automaton.getInitialState());
        initialGV.setStates(initialStates);
        initialGV.setCurrentState(automaton.advance(initialGV));
        initialGV.setCut(vectorClock);
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
        if (pid.equals(token.getOwner())) {
            Log.d("monitor", "Sending a token back home. To: " + pid.toString());
        } else {
            Log.d("monitor", "Sending a token. To: " + pid.toString());
        }
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
        TokenSender.bulkSendTokens();
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
        gv.setCurrentState(automaton.advance(gv));
        handleMonitorStateChange(gv);
        checkOutgoingTransitions(gv, event);
    }

    private static void handleMonitorStateChange(@NonNull final GlobalView gv) {
        final Automaton.Evaluation state = gv.getCurrentState().getStateType();
        switch (state) {
            case SATISFIED:
                networkServiceConnection.getNetworkLayer().signalMonitorSatisfied();
                break;
            case VIOLATED:
                networkServiceConnection.getNetworkLayer().signalMonitorViolated();
                break;
            default:
                return;
        }
        Log.d("monitor", "Monitor state changed! " + state + " in state " + gv.getCurrentState().getStateName());
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
        for (final AutomatonTransition trans : automaton.getTransitions()) {
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

        final List<Token> pendingSend = new ArrayList<>();
        for (final Map.Entry<NetworkPeerIdentifier, Set<AutomatonTransition>> entry : consult.entrySet()) {
            Token.Builder builder = new Token.Builder(monitorID, entry.getKey());
            for (Token token : pendingSend) {
                VectorClock.Comparison comparison = token.getCut().compareToClock(event.getVC());
                if (token.getDestination().equals(entry.getKey())
                        && comparison == VectorClock.Comparison.EQUAL
                        && token.getTargetEventId() == gv.getCut().process(entry.getKey()) + 1) {
                    //Modify one of the pending tokens
                    builder = new Token.Builder(token);
                    pendingSend.remove(token);
                    break;
                }
            }

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
            final Token token = builder.targetEventId(gv.getCut().process(entry.getKey()) + 1)
                    .cut(event.getVC()).conjuncts(forToken).automatonTransitions(entry.getValue())
                    .build();
            pendingSend.add(token);
        }
        gv.addTokens(pendingSend);
        TokenSender.bulkTokenSendOut(pendingSend);
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
        if (token.getOwner().equals(monitorID)) {
            final List<GlobalView> globalViews = getGlobalView(token);
            for (final GlobalView globalView : globalViews) {
                globalView.updateWithToken(token);
                boolean hasEnabled = false;
                for (final AutomatonTransition trans : token.getAutomatonTransitions()) {
                    // Get other tokens for same transition
                    final List<Token> tokens = globalView.getTokensForTransition(trans);
                    if (trans.enabled(globalView, tokens)) {
                        hasEnabled = true;
                    }
                    if (hasEnabled && consistent(globalView, tokens)) {
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
                        handleMonitorStateChange(gvn1);
                        processEvent(gvn1, gvn1.getPendingEvents().remove());
                        processEvent(gvn2, history.get(gvn2.getCut().process(monitorID)));
                    } else {
                        globalView.getPendingTransitions().remove(trans);
                    }
                }
                if (globalView.getPendingTransitions().isEmpty()) {
                    if (hasEnabled) {
                        GV.remove(globalView);
                    } else {
                        globalView.setTokens(new ArrayList<>());
                        processEvent(globalView, globalView.getPendingEvents().remove());
                    }
                }

                final Token maxConjuncts = globalView.getTokenWithMostConjuncts();
                TokenSender.sendTokenOut(maxConjuncts);
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
        final VectorClock.Comparison comp = token.getCut().compareToClock(event.getVC());
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
            final Event targetEvent = history.get(token.getTargetEventId());
            final Token newToken = new Token.Builder(token).cut(targetEvent.getVC()).conjuncts(conjunctsMap).targetProcessState(targetEvent.getState()).build();
            TokenSender.sendTokenHome(newToken);
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
            TokenSender.sendTokenHome(newToken);
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
                if (token.getUniqueLocalIdentifier() == t.getUniqueLocalIdentifier()) {
                    ret.add(gv);
                    break;
                }
            }
        }
        return ret;
    }
}
