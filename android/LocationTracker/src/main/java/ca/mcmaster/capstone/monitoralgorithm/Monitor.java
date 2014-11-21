package ca.mcmaster.capstone.monitoralgorithm;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/* Class to hold the main algorithm code.*/
public class Monitor {
    private static final List<Event> history = new ArrayList<Event>();
    private static final Set<Token> waitingTokens = new LinkedHashSet<Token>();
    private static final int monitorID = 0; // TODO: make this equal something reasonable
    private static final Set<GlobalView> GV = new LinkedHashSet<GlobalView>();
    private static final int numProcesses = 10;

    // Placeholders until it becomes clear where these methods will really come from.
    private static Token receive() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
    private static Event read() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
    private static void send(Token token, int pid) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    // These methods are either not described in the paper or are described separately from the main
    // body of the algorithm. They will be implemented in a future commit.
    private static void mergeSimilarGlobalViews(Set<GlobalView> views) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
    private static GlobalView getGlobalView(Token token) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
    private static boolean enabled(AutomatonTransition transition, List<Token> tokens){
        throw new UnsupportedOperationException("Not implemented yet.");
    }
    // Checks to make sure the vector clock of each token is consistent with this process's
    private static boolean consistent(List<Token> tokens){
        throw new UnsupportedOperationException("Not implemented yet.");
    }


    /*
     * Perform some basic initialization.
     *
     * @param initialStates The initial state of each known process.
     */
    public static void init(List<ProcessState> initialStates) {
        GlobalView initialGV = new GlobalView();
        initialGV.setCurrentState(Automaton.getInitialState());
        initialGV.setStates(initialStates);
        initialGV.setCurrentState(Automaton.advance(initialGV));
    }

    /*
     * The main loop of the algorithm.
     *
     * @param initialStates The initial state of each known process.
     */
    public static void monitorLoop(List<ProcessState> initialStates) {
        init(initialStates);

        while (true) {
            Token receivedToken = receive();
            // TODO: add check for empty token and empty event if it turns out such a thing is possible
            receiveToken(receivedToken);
            Event localEvent = read();
            receiveEvent(localEvent);
        }
    }

    /*
     * Process local events for global views who have no pending events waiting to be processed.
     *
     * @param event The event to be processed.
     */
    public static void receiveEvent(Event event) {
        history.add(event.eid(), event);
        for (Iterator<Token> i = waitingTokens.iterator(); i.hasNext(); ) {
            Token t = i.next();
            if (t.getTargetEventId() == event.eid()) {
                processToken(t, event);
                i.remove();
            }
        }
        mergeSimilarGlobalViews(GV);
        for (GlobalView gv : GV) {
            gv.getPendingEvents().add(event);
            if (gv.getTokens().isEmpty()) {
                //XXX: not really sure why the algorithm specifies these being passes separately.
                processEvent(gv, gv.getPendingEvents().remove());
            }
        }
    }

    /*
     * Compute the next state of the monitor automaton. Depending on the information needed to
     * evaluate the transitions of the monitor automaton this may be done locally or there may be a
     * need to consult with another process.
     *
     * @param gv The global view to compute the next monitor state for.
     * @param event The event to be evaluated.
     */
    public static void processEvent(GlobalView gv, Event event) {
        gv.setCut(gv.getCut().merge(event.getVC()));
        ProcessState state = gv.getStates().get(monitorID);
        gv.getStates().set(monitorID, state.update(event));
        if (gv.isConsistent()) {
            gv.setCurrentState(Automaton.advance(gv));
            if (Automaton.getEvaluation() == Automaton.Evaluation.SATISFIED) {
                Log.d("processEvent", "I am satisfied!");
            } else if (Automaton.getEvaluation() == Automaton.Evaluation.VIOLATED) {
                Log.d("processEvent", "I feel violated!");
            }
        }
        checkOutgoingTransitions(gv, event);
    }

    private static void checkOutgoingTransitions(GlobalView gv, Event event) {
        List<Set<AutomatonTransition>> consult = new ArrayList<>();
        for (AutomatonTransition trans : Automaton.getTransitions()) {
            AutomatonState current = gv.getCurrentState();
            if (trans.getFrom() == current && trans.getTo() != current) {
                Set<Integer> participating = trans.getParticipatingProcesses();
                Set<Integer> forbidding = trans.getForbiddingProcesses(gv);
                if (!forbidding.contains(monitorID)) {
                    Set<Integer> inconsistent = gv.getInconsistentProcesses();
                    // intersection
                    participating.retainAll(inconsistent);
                    // union
                    forbidding.addAll(participating);
                    for (Integer process : forbidding) {
                        gv.getPendingTransitions().add(trans);
                        consult.get(process).add(trans);
                    }
                }
            }
        }
        for (int j = 0; j < numProcesses; ++j) {
            if (!consult.get(j).isEmpty()) {
                // Get all the conjuncts for process j
                Set<Conjunct> conjuncts = new HashSet<>();
                for (AutomatonTransition trans : consult.get(j)) {
                    conjuncts.addAll(trans.getConjuncts());
                }
                Token token = new Token.Builder(monitorID, j).targetEventId(gv.getCut().process(j) + 1)
                        .cut(event.getVC()).conjuncts(conjuncts).automatonTransitions(consult.get(j))
                        .build();
                gv.getTokens().add(token);
            }
        }
        Token token = gv.getTokenWithMostConjuncts();
        send(token, token.getDestination());
        token = new Token.Builder(token).sent(true).build();
    }

    /*
     * The method does two things:
     *     1) enable or disable automaton transitions based on the received token
     *     2) evaluate the transition that the received token is requesting
     *
     * @param token The token being received.
     */
    public static void receiveToken(Token token) {
        if (token.getOwner() == monitorID) {
            GlobalView gv = getGlobalView(token);
            gv.update(token);
            for (AutomatonTransition trans : token.getAutomatonTransitions()) {
                // Get other tokens for same transition
                List<Token> tokens = gv.getTokensForTransition(trans);
                if (enabled(trans, tokens) && consistent(tokens)) {
                    for (Token tok : tokens) {
                        gv.update(tok);
                    }
                    GlobalView gvn1 = new GlobalView(gv);
                    GlobalView gvn2 = new GlobalView(gv);
                    gvn1.setCurrentState(trans.getTo());
                    gvn2.setCurrentState(trans.getTo());
                    gvn1.setTokens(new ArrayList<Token>());
                    gvn2.setTokens(new ArrayList<Token>());
                    gv.getPendingTransitions().remove(trans);
                    GV.add(gvn1);
                    GV.add(gvn2);
                    if (Automaton.getEvalForState(gvn1.getCurrentState()) == Automaton.Evaluation.SATISFIED) {
                        Log.d("processEvent", "I am satisfied!");
                    } else if (Automaton.getEvalForState(gvn1.getCurrentState()) == Automaton.Evaluation.VIOLATED) {
                        Log.d("processEvent", "I feel violated!");
                    }
                    processEvent(gvn1, gvn1.getPendingEvents().remove());
                    processEvent(gvn2, history.get(gvn2.getCut().process(monitorID)));
                } else if (trans.getEvaluation() == Conjunct.Evaluation.FALSE) {
                    gv.getPendingTransitions().remove(trans);
                }
            }
            if (gv.getPendingTransitions().isEmpty()) {
                boolean hasEnabled = false;
                for (AutomatonTransition gvTrans : gv.getPendingTransitions()) {
                    if (gvTrans.getEvaluation() == Conjunct.Evaluation.TRUE) {
                        GV.remove(gv);
                        hasEnabled = true;
                        break;
                    }
                }
                if (!hasEnabled) {
                    gv.setTokens(new ArrayList<Token>());
                    processEvent(gv, gv.getPendingEvents().remove());
                }
            }
            Token maxConjuncts = gv.getTokenWithMostConjuncts();
            send(maxConjuncts, maxConjuncts.getDestination());
            maxConjuncts = new Token.Builder(maxConjuncts).sent(true).build();
        } else if (token.getOwner() != monitorID) {
            boolean hasTarget = false;
            for (Event event : history) {
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
    public static void processToken(Token token, Event event) {
        // TODO: define TokenBuilder, use to do assgns here
        if (event.getVC().compareToClock(token.getCut()) == VectorClock.Comparison.CONCURRENT) {
            evaluateToken(token, event);
        } else {
            for (Conjunct conjunct : token.getConjuncts()) {
                conjunct.setEvaluation(Conjunct.Evaluation.FALSE);
            }
            Event eventPrime = history.get(token.getTargetEventId());
            Token newToken = new Token.Builder(token).cut(eventPrime.getVC()).targetProcessState(eventPrime.getState()).build();
            send(newToken, newToken.getOwner());
        }
    }

    /*
     * Evaluates each of token's predicates.
     *
     * @param token The token whose predicates will be evaluated.
     * @param event The
     */
    public static void evaluateToken(Token token, Event event) {
        token.evaluateConjuncts(event);
        if (token.anyConjunctSatisfied()) {
            Token newToken = new Token.Builder(token).cut(event.getVC()).targetProcessState(event.getState()).build();
            send(newToken, newToken.getOwner());
        } else {
            waitingTokens.add(token.waitForNextEvent());
        }
    }
}
