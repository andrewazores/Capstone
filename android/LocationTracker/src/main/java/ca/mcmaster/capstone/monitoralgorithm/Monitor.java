package ca.mcmaster.capstone.monitoralgorithm;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    public final static String LOG_TAG = "monitor";

    private final Map<Integer, Event> history = new HashMap<>();
    private final Set<Token> waitingTokens = new LinkedHashSet<>();
    private final Set<GlobalView> GV = new HashSet<>();
    private NetworkPeerIdentifier monitorID = null;
    private ExecutorService workQueue;
    private volatile boolean cancelled = false;
    private Future<?> monitorJob = null;
    private Future<?> tokenPollJob = null;
    private Future<?> eventPollJob = null;
    private Intent networkServiceIntent;
    private Intent initializerServiceIntent;
    private static final NetworkServiceConnection networkServiceConnection = new NetworkServiceConnection();
    private final InitializerServiceConnection initializerServiceConnection = new InitializerServiceConnection();
    private final Automaton automaton = Automaton.INSTANCE;

    /* Class to abstract the bulk sending of tokens. */
    private static class TokenSender {
        private static final Set<Token> tokensToSendOut = new HashSet<>();
        private static final Set<Token> tokensToSendHome = new HashSet<>();

        public static synchronized Set<Token> getTokensToSendOut() {
            return new HashSet(tokensToSendOut);
        }

        private static synchronized void bulkTokenSendOut(final Set<Token> tokens) {
            Log.d(LOG_TAG, "Queued the following tokens to send out: " + tokens.toString());
            tokensToSendOut.addAll(tokens);
        }

        private static synchronized void sendTokenOut(final Token token) {
            Log.d(LOG_TAG, "Queued the following token to send out." + token.toString());
            tokensToSendOut.add(token);
        }

        private static synchronized void sendTokenHome(final Token token) {
            Log.d(LOG_TAG, "Queued the following token to send home: " + token.toString());
            tokensToSendHome.add(token);
        }

        private static synchronized void bulkSendTokens() {
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

    public Monitor() {}

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

        monitorJob = workQueue.submit(this::monitorLoop);
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

    private void cancelJobs(@NonNull final Future<?> ... jobs) {
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
    public void init() {
        Log.d(LOG_TAG, "Initializing monitor");
        while (networkServiceConnection.getNetworkLayer() == null && !cancelled) {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                Log.d(LOG_TAG, "NetworkLayer connection is not established: " + e.getLocalizedMessage());
            }
        }


        monitorID = initializerServiceConnection.getInitializer().getLocalPID();
        final Map<String, NetworkPeerIdentifier> virtualIdentifiers = initializerServiceConnection.getInitializer().getVirtualIdentifiers();

        automaton.processAutomatonFile(initializerServiceConnection.getInitializer().getAutomatonFile(),
                initializerServiceConnection.getInitializer().getConjunctMap(),
                virtualIdentifiers);

        //FIXME: This is pretty messy. We can probably do better given some time to think.
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
            valuations.put(currentProcess, new Valuation(val));
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
        synchronized (GV) {
            GV.add(initialGV);
        }
        Log.d(LOG_TAG, "Finished initializing monitor");
    }

    /*
     * The main loop of the algorithm.
     *
     * @param initialStates The initial state of each known process.
     */
    public void monitorLoop() {
        init();
        Log.d(LOG_TAG, "submitting loop tasks");
        tokenPollJob = Executors.newSingleThreadExecutor().submit(this::pollTokens);
        eventPollJob = Executors.newSingleThreadExecutor().submit(this::pollEvents);
    }

    private void pollTokens() {
        while (!cancelled) {
            Log.d(LOG_TAG, "pollTokens looping");
            final Token token = receive();
            if (token != null) {
                workQueue.submit(() -> receiveToken(token));
            }
        }
        Log.d(LOG_TAG, "pollTokens exiting");
    }

    private void pollEvents() {
        while (!cancelled) {
            Log.d(LOG_TAG, "pollEvents looping");
            final Event event = read();
            if (event != null) {
                workQueue.submit(() -> receiveEvent(event));
            }
        }
        Log.d(LOG_TAG, "pollEvents exiting");
    }

    private Token receive() {
        Log.d(LOG_TAG, "receive entered");
        Token token = null;
        while (token == null && !cancelled) {
            try {
                token = networkServiceConnection.getNetworkLayer().receiveToken();
            } catch (InterruptedException e) {
                Log.d("thread", "Woke up without a token, trying again? " + cancelled + " : " + e.getLocalizedMessage());
            }
        }
        Log.d(LOG_TAG, "receive exited");
        return token;
    }

    private static void send(@NonNull final Token token, @NonNull final NetworkPeerIdentifier pid) {
        if (pid.equals(token.getOwner())) {
            Log.d(LOG_TAG, "Sending a token back home. Token: " + token.toString());
        } else {
            Log.d(LOG_TAG, "Sending a token. Token: " + token.toString());
        }
        networkServiceConnection.getNetworkLayer().sendTokenToPeer(pid, token);
    }

    private Event read() {
        Log.d(LOG_TAG, "read entered");
        Event event = null;
        while (event == null && !cancelled) {
            try {
                event = networkServiceConnection.getNetworkLayer().receiveEvent();
            } catch (InterruptedException e) {
                Log.d("thread", "Woke up without an event, trying again? " + cancelled + " : " + e.getLocalizedMessage());
            }
        }
        Log.d(LOG_TAG, "read exited");
        return event;
    }

    /*
     * Process local events for global views who have no pending events waiting to be processed.
     *
     * @param event The event to be processed.
     */
    public void receiveEvent(@NonNull final Event event) {
        Log.d(LOG_TAG, "Entering receiveEvent. Event: " + event.toString());
        synchronized (history) {
            history.put(event.getEid(), event);
        }
        // We need to make a copy of waitingTokens to iterate over since tokens may be added to the set later, which invalidates the iterator
        synchronized (waitingTokens) {
            final Set<Token> tokensToProcess = Collections.unmodifiableSet(new HashSet<>(waitingTokens));
            waitingTokens.clear();
            for (final Token token : tokensToProcess) {
                if (token.getTargetEventId() == event.getEid()) {
                    processToken(token, event);
                }
            }
        }
        synchronized (GV) {
            final Set<GlobalView> copyGV = new HashSet<>(GV);
            GV.clear();
            GV.addAll(mergeSimilarGlobalViews(copyGV));
            for (final GlobalView gv : GV) {
                Log.d(LOG_TAG, "globalView inside receiveEvent: " + gv.toString());
                gv.getPendingEvents().add(event);
                if (gv.getTokens().isEmpty()) {
                    processEvent(gv, gv.getPendingEvents().remove());
                }
            }
        }
        TokenSender.bulkSendTokens();
        Log.d(LOG_TAG, "Exiting receiveEvent.");
    }

    private Set<GlobalView> mergeSimilarGlobalViews(@NonNull final Collection<GlobalView> gv) {
        Log.d(LOG_TAG, "Entering mergeSimilarGlobalViews with " + gv.size() + " globalViews.");
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
        Log.d(LOG_TAG, "Leaving mergeSimilarGlobalViews with " + merged.size() + " globalViews.");
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
    public void processEvent(@NonNull final GlobalView gv, @NonNull final Event event) {
        Log.d(LOG_TAG, "Entering processEvent, Event: " + event.toString());
        gv.updateWithEvent(event);
        gv.setCurrentState(automaton.advance(gv));
        handleMonitorStateChange(gv);
        checkOutgoingTransitions(gv, event);
        Log.d(LOG_TAG, "Exiting processEvent");
    }

    /*
     * This method checks if the current state in gv is a satisfaction or violation state. In either
     * case this method then returns all tokens in waitingTokens. If the current state is undecided
     * nothing happens.
     *
     * @param gv The GlobalView to check the state of.
     */
    private void handleMonitorStateChange(@NonNull final GlobalView gv) {
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
        Log.d(LOG_TAG, "Monitor state changed! " + state + " in state " + gv.getCurrentState().getStateName());

        // Send all waiting tokens home with the local state
        synchronized (waitingTokens) {
            for (Token token : waitingTokens) {
                TokenSender.sendTokenHome(new Token.Builder(token).cut(token.getCut().merge(gv.getCut()))
                        .targetProcessState(gv.getStates().get(monitorID)).build());
            }
            waitingTokens.clear();
        }
        TokenSender.bulkSendTokens();
        cancelled = true;
        workQueue.shutdownNow();
        monitorJob.cancel(false);
        tokenPollJob.cancel(false);
        eventPollJob.cancel(false);
    }

    /*
     * Identifies events in gv that are concurrent with event that can enable out going transitions
     * from the current state of the monitor automaton.
     *
     * @param gv The global view to use for finding concurrent events.
     * @param event The event to find concurrent events for.
     */
    private void checkOutgoingTransitions(@NonNull final GlobalView gv, @NonNull final Event event) {
        Log.d(LOG_TAG, "Entering checkOutgoingTransitions. \n    globalView: " + gv.toString() + "\n    event: " + event.toString());
        final Map<NetworkPeerIdentifier, Set<AutomatonTransition>> consult = new HashMap<>();
        for (final AutomatonTransition trans : automaton.getTransitions()) {
            final Set<Conjunct> forbiddingConjuncts = new HashSet<>();
            final AutomatonState current = gv.getCurrentState();
            if (trans.getFrom().equals(current) && !trans.getTo().equals(current)) {
                final Set<NetworkPeerIdentifier> participating = trans.getParticipatingProcesses();
                forbiddingConjuncts.addAll(trans.getForbiddingConjuncts(gv));
                final Set<NetworkPeerIdentifier> forbidding = new HashSet<>();
                // Extract NetworkPeerIdentifiers for forbidding processes from the set of forbidding conjuncts
                for (final Conjunct conjunct : forbiddingConjuncts) {
                    forbidding.add(conjunct.getOwnerProcess());
                }
                if (!forbidding.contains(monitorID)) {
                    final Set<NetworkPeerIdentifier> inconsistent = gv.getInconsistentProcesses(monitorID);
                    // intersection
                    participating.retainAll(inconsistent);
                    // union
                    forbidding.addAll(participating);
                    for (final NetworkPeerIdentifier process : forbidding) {
                        gv.addPendingTransition(trans);
                        if (consult.get(process) == null) {
                            consult.put(process, new HashSet<>());
                        }
                        consult.get(process).add(trans);
                    }
                }
            }
        }

        final Set<Token> pendingSend = TokenSender.getTokensToSendOut();
        for (final Map.Entry<NetworkPeerIdentifier, Set<AutomatonTransition>> entry : consult.entrySet()) {
            final NetworkPeerIdentifier destination = entry.getKey();
            Log.d(LOG_TAG, "Need to send a token to: " + destination + "\n    to gather information about these transitions: " + entry.getValue());
            Token.Builder builder = new Token.Builder(monitorID, destination);
            for (Iterator<Token> it = pendingSend.iterator(); it.hasNext(); ) {
                final Token token = it.next();
                VectorClock.Comparison comparison = token.getCut().compareToClock(event.getVC());
                if (token.getDestination().equals(destination)
                        && comparison == VectorClock.Comparison.EQUAL
                        && token.getTargetEventId() == gv.getCut().process(destination) + 1) {
                    //Modify one of the pending tokens
                    builder = new Token.Builder(token);
                    it.remove();
                    break;
                }
            }

            // Get all the conjuncts for process j
            final Set<Conjunct> conjuncts = new HashSet<>();
            for (AutomatonTransition transition : entry.getValue()) {
                for (Conjunct conjunct : transition.getConjuncts()) {
                    if (conjunct.getOwnerProcess().equals(destination)) {
                        conjuncts.add(conjunct);
                    }
                }
            }

            //Build map to add to token
            final Map<Conjunct, Conjunct.Evaluation> forToken = new HashMap<>();
            for (final Conjunct conjunct : conjuncts) {
                forToken.put(conjunct, Conjunct.Evaluation.NONE);
            }
            final Token token = builder.targetEventId(gv.getCut().process(destination) + 1)
                    .cut(event.getVC()).conjuncts(forToken).automatonTransitions(entry.getValue())
                    .build();
            pendingSend.add(token);
        }
        gv.addTokens(new ArrayList(pendingSend));
        TokenSender.bulkTokenSendOut(pendingSend);
        Log.d(LOG_TAG, "Exiting checkOutgoingTransitions");
    }

    /*
     * The method does two things:
     *     1) enable or disable automaton transitions based on the received token
     *     2) evaluate the transition that the received token is requesting
     *
     * @param token The token being received.
     */
    public void receiveToken(@NonNull final Token token) {
        Log.d(LOG_TAG, "Entering receiveToken. Token: " + token.toString());
        if (token.getOwner().equals(monitorID)) {
            final List<GlobalView> globalViews = getGlobalView(token);
            for (final GlobalView globalView : globalViews) {
                globalView.updateWithToken(token);
                boolean hasEnabled = false;
                for (final AutomatonTransition trans : token.getAutomatonTransitions()) {
                    Log.d(LOG_TAG, "Checking if transition is enabled: " + trans.toString());
                    // Get other tokens for same transition
                    final List<Token> tokens = globalView.getTokensForTransition(trans);
                    for (Token tok : tokens) {
                        if (!tok.isReturned()) {
                            Log.d(LOG_TAG, "Not all tokens for this transition have been returned. Could not find: " + tok);
                            return;
                        }
                    }
                    if (trans.enabled(globalView, tokens) && globalView.consistent(trans)) {
                        hasEnabled |= true;
                        Log.d(LOG_TAG, "The transition is enabled and the global view is consistent.");
                        globalView.reduceStateFromTokens(tokens);
                        globalView.removePendingTransition(trans);
                        final GlobalView gvn1 = new GlobalView(globalView);
                        final GlobalView gvn2 = new GlobalView(globalView);
                        gvn1.setCurrentState(trans.getTo());
                        gvn2.setCurrentState(trans.getTo());
                        gvn1.setTokens(new ArrayList<>());
                        gvn2.setTokens(new ArrayList<>());
                        synchronized (GV) {
                            GV.add(gvn1);
                            Log.d(LOG_TAG, "gvn1: " + gvn1.toString());
                            GV.add(gvn2);
                            Log.d(LOG_TAG, "gvn2: " + gvn1.toString());
                        }
                        handleMonitorStateChange(gvn1);
                        processEvent(gvn1, gvn1.getPendingEvents().remove());
                        synchronized (history) {
                            processEvent(gvn2, history.get(gvn2.getCut().process(monitorID)));
                        }
                    } else {
                        Log.d("moonitor", "Removing a pending transition from the global view.");
                        globalView.removePendingTransition(trans);
                    }
                }
                if (globalView.getPendingTransitions().isEmpty()) {
                    if (hasEnabled) {
                        synchronized (GV) {
                            Log.d(LOG_TAG, "Removing a global view.");
                            GV.remove(globalView);
                        }
                    } else {
                        globalView.setTokens(new ArrayList<>());
                        while (!globalView.getPendingEvents().isEmpty()) {
                            Log.d(LOG_TAG, "Processing pending event");
                            processEvent(globalView, globalView.getPendingEvents().remove());
                        }
                    }
                } else {
                    final Token maxConjuncts = globalView.getTokenWithMostConjuncts();
                    if (maxConjuncts != null) {
                        TokenSender.sendTokenOut(maxConjuncts);
                    }
                }
            }
        } else {
            boolean hasTarget = false;
            synchronized (history) {
                for (final Event event : history.values()) {
                    if (event.getEid() == token.getTargetEventId()) {
                        processToken(token, event);
                        hasTarget = true;
                        break;
                    }
                }
            }
            if (!hasTarget) {
                synchronized (waitingTokens) {
                    Log.d(LOG_TAG, "Adding a token to waitingTokens.");
                    waitingTokens.add(token);
                }
            }
        }
        TokenSender.bulkSendTokens();
        Log.d(LOG_TAG, "Exiting receiveToken.");
    }

    /*
     * Decide whether token should be returned to its owner. Token is updated with event.
     *
     * @param token The token to process.
     * @param event The event to update token with.
     */
    public void processToken(@NonNull final Token token, @NonNull final Event event) {
        Log.d(LOG_TAG, "Entering processToken.\n    token: " + token.toString() + "\n    event: " + event.toString());
        final VectorClock.Comparison comp = token.getCut().compareToClock(event.getVC());
        if (comp == VectorClock.Comparison.CONCURRENT || comp == VectorClock.Comparison.EQUAL) {
            evaluateToken(token, event);
        } else if (comp == VectorClock.Comparison.BIGGER) {
            synchronized (waitingTokens) {
                Log.d(LOG_TAG, "Waiting for next event.");
                waitingTokens.add(token.waitForNextEvent());
            }
        } else {
            final Map<Conjunct, Conjunct.Evaluation> conjunctsMap = token.getConjunctsMap();
            for (final Conjunct conjunct : conjunctsMap.keySet()) {
                conjunctsMap.put(conjunct, Conjunct.Evaluation.FALSE);
            }
            synchronized (history) {
                final Event targetEvent = history.get(token.getTargetEventId());
                final Token newToken = new Token.Builder(token).cut(targetEvent.getVC()).conjuncts(conjunctsMap).targetProcessState(targetEvent.getState()).build();
                TokenSender.sendTokenHome(newToken);
            }
        }
        Log.d(LOG_TAG, "Exiting processToken");
    }

    /*
     * Evaluates each of token's predicates.
     *
     * @param token The token whose predicates will be evaluated.
     * @param event The event to use to evaluate the token.
     */
    public void evaluateToken(@NonNull final Token token, @NonNull final Event event) {
        Log.d(LOG_TAG, "Entering evaluateToken.\n    token: " + token.toString() + "\n    event: " + event.toString());
        token.evaluateConjuncts(event);
        if (token.anyConjunctSatisfied()) {
            final Token newToken = new Token.Builder(token).cut(event.getVC()).targetProcessState(event.getState()).build();
            TokenSender.sendTokenHome(newToken);
        } else {
            synchronized (history) {
                int nextEvent = token.getTargetEventId() + 1;
                if (history.containsKey(nextEvent)) {
                    Log.d(LOG_TAG, "Processing token with next event.");
                    processToken(token.waitForNextEvent(), history.get(nextEvent));
                } else {
                    Log.d(LOG_TAG, "Adding a token to waitingTokens.");
                    synchronized (waitingTokens) {
                        waitingTokens.add(token.waitForNextEvent());
                    }
                }
            }
        }
        Log.d(LOG_TAG, "Exiting evaluateToken");
    }

    /*
     * Returns a list of global views that contain a copy of token.
     *
     * @param token The token to search for.
     * @return A list of GlobalViews that have a copy of token.
     */
    private List<GlobalView> getGlobalView(@NonNull final Token token) {
        final List<GlobalView> ret = new ArrayList<>();
        synchronized (GV) {
            for (final GlobalView gv : GV) {
                for (final Token t : gv.getTokens()) {
                    if (token.getUniqueLocalIdentifier() == t.getUniqueLocalIdentifier()) {
                        ret.add(gv);
                        break;
                    }
                }
            }
        }
        return ret;
    }
}
