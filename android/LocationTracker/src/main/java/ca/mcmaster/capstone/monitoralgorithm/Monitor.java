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

import ca.mcmaster.capstone.networking.CapstoneService;
import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;

/* Class to hold the main algorithm code.*/
public class Monitor extends Service {
    private static final List<Event> history = new ArrayList<>();
    private static final Set<Token> waitingTokens = new LinkedHashSet<>();
    private static HashableNsdServiceInfo monitorID = null;
    private static final Set<GlobalView> GV = new HashSet<>();
    private static final int numProcesses = 10;
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
        networkServiceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(networkServiceIntent, serviceConnection, BIND_AUTO_CREATE);
        runMonitor = true;
        thread = new Thread(() -> monitorLoop(Collections.emptyMap()));
        Log.d("thread", "Started monitor!");
        thread.start();
    }

    @Override
    public void onDestroy() {
        Log.d("thread", "Stopped monitor!");
        runMonitor = false;
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

    private static void send(final Token token, final HashableNsdServiceInfo pid) {
        while (serviceConnection.getNetworkLayer() == null) {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                Log.d("thread", "NetworkLayer connection is not established: " + e.getLocalizedMessage());
            }
        }
        while (token == null) {
            serviceConnection.getNetworkLayer().sendTokenToPeer(/*FIXME: should this be pid?*/token.getDestination(), token);
        }
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

    // These methods are either not described in the paper or are described separately from the main
    // body of the algorithm. They will be implemented in a future commit.
    private static boolean enabled(final AutomatonTransition transition, final List<Token> tokens){
        throw new UnsupportedOperationException("Not implemented yet.");
    }
    // Checks to make sure the vector clock of each token is consistent with this process's
    private static boolean consistent(final List<Token> tokens){
        throw new UnsupportedOperationException("Not implemented yet.");
    }


    /*
     * Perform some basic initialization.
     *
     * @param initialStates The initial state of each known process.
     */
    public static void init(final Map<HashableNsdServiceInfo, ProcessState> initialStates) {
        final GlobalView initialGV = new GlobalView();
        monitorID = HashableNsdServiceInfo.get(serviceConnection.getNetworkLayer().getLocalNsdServiceInfo());

        initialGV.setCurrentState(Automaton.getInitialState());
        initialGV.setStates(initialStates);
        initialGV.setCurrentState(Automaton.advance(initialGV));
    }

    /*
     * The main loop of the algorithm.
     *
     * @param initialStates The initial state of each known process.
     */
    public static void monitorLoop(final Map<HashableNsdServiceInfo, ProcessState> initialStates) {
        init(initialStates);

        while (runMonitor) {
            final Token receivedToken = receive();
            // TODO: add check for empty token and empty event if it turns out such a thing is possible
            receiveToken(receivedToken);
            final Event localEvent = read();
            receiveEvent(localEvent);
        }
        Log.d("thread", "Left monitor loop.");
    }

    /*
     * Process local events for global views who have no pending events waiting to be processed.
     *
     * @param event The event to be processed.
     */
    public static void receiveEvent(final Event event) {
        history.add(event.eid(), event);
        for (Iterator<Token> i = waitingTokens.iterator(); i.hasNext();) {
            final Token t = i.next();
            if (t.getTargetEventId() == event.eid()) {
                processToken(t, event);
                i.remove();
            }
        }
        Set<GlobalView> copyGV = new HashSet<>(GV);
        GV.clear();
        GV.addAll(mergeSimilarGlobalViews(copyGV));
        for (final GlobalView gv : GV) {
            gv.getPendingEvents().add(event);
            if (gv.getTokens().isEmpty()) {
                processEvent(gv, gv.getPendingEvents().remove());
            }
        }
    }

    private static Set<GlobalView> mergeSimilarGlobalViews(final Collection<GlobalView> gv) {
        final Iterator<GlobalView> it1 = gv.iterator();
        final Iterator<GlobalView> it2 = gv.iterator();
        Set<GlobalView> merged = new HashSet<>();
        while (it1.hasNext()) {
            final GlobalView gv1 = it1.next();
            while (it2.hasNext()) {
                final GlobalView gv2 = it1.next();
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
    public static void processEvent(final GlobalView gv, final Event event) {
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
    private static void checkOutgoingTransitions(final GlobalView gv, final Event event) {
        final Map<HashableNsdServiceInfo, Set<AutomatonTransition>> consult = new HashMap<>();
        for (AutomatonTransition trans : Automaton.getTransitions()) {
            final AutomatonState current = gv.getCurrentState();
            if (trans.getFrom() == current && trans.getTo() != current) {
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

        for (Map.Entry<HashableNsdServiceInfo, Set<AutomatonTransition>> entry : consult.entrySet()) {
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
        Token token = gv.getTokenWithMostConjuncts();
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
    public static void receiveToken(final Token token) {
        if (token.getOwner() == monitorID) {
            final List<GlobalView> globalViews = getGlobalView(token);
            for (final GlobalView globalView : globalViews) {
                globalView.update(token);
                for (final AutomatonTransition trans : token.getAutomatonTransitions()) {
                    // Get other tokens for same transition
                    final List<Token> tokens = globalView.getTokensForTransition(trans);
                    if (enabled(trans, tokens) && consistent(tokens)) {
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
                    } else if (trans.getEvaluation() == Conjunct.Evaluation.FALSE) {
                        globalView.getPendingTransitions().remove(trans);
                    }
                }
                if (globalView.getPendingTransitions().isEmpty()) {
                    boolean hasEnabled = false;
                    for (final AutomatonTransition gvTrans : globalView.getPendingTransitions()) {
                        if (gvTrans.getEvaluation() == Conjunct.Evaluation.TRUE) {
                            GV.remove(globalViews); // FIXME: GV is Set<GlobalView>, globalViews is List<GlobalView>. Should this be removeAll() instead?
                            hasEnabled = true;
                            break;
                        }
                    }
                    if (!hasEnabled) {
                        globalView.setTokens(new ArrayList<>());
                        processEvent(globalView, globalView.getPendingEvents().remove());
                    }
                }
                Token maxConjuncts = globalView.getTokenWithMostConjuncts();
                send(maxConjuncts, maxConjuncts.getDestination());
                maxConjuncts.setSent(true);
            }
        } else {
            boolean hasTarget = false;
            for (final Event event : history) {
                if (event.eid() == token.getTargetEventId()) {
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
     * Decide whether token should be returned to its owner. Token is updated with event.
     *
     * @param token The token to process.
     * @param event The event to update token with.
     */
    public static void processToken(final Token token, final Event event) {
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
    public static void evaluateToken(final Token token, final Event event) {
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
    private static List<GlobalView> getGlobalView(final Token token) {
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
