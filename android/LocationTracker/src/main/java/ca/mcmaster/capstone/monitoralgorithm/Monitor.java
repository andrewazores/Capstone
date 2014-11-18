package ca.mcmaster.capstone.monitoralgorithm;

import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/* Class to hold the main algorithm code.*/
public class Monitor {
    private static final List<Event> history = new ArrayList<Event>();
    private static final Set<Token> waitingTokens = new LinkedHashSet<Token>();
    private static final Automaton automaton = new Automaton();
    private static final int monitorID = 0; // TODO: make this equal something reasonable
    private static final Set<GlobalView> GV = new LinkedHashSet<GlobalView>();

    // Placeholders until it becomes clear where these methods will really come from.
    private static Token receive() { return null; }
    private static Event read() { return null; }
    private static void send(Token token, int pid) {}

    // These methods are either not described in the paper or are described separately from the main
    // body of the algorithm. They will be implemented in a future commit.
    private static void mergeSimilarGlobalViews(Set<GlobalView> views) {}
    private static void checkOutgoingTransitions(GlobalView gv) {}
    private static GlobalView getGlobalView(Token token) { return null; }


    /*
     * Perform some basic initialization.
     *
     * @param initialStates The initial state of each known process.
     */
    public static void init(List<ProcessState> initialStates) {
        // XXX: I'm not sure if the GlobalView needs to know the current state of the automaton. It
        //      seems like the GlobalView is tracking something that the Automaton itself should
        //      keep.
        GlobalView initialGV = new GlobalView();
        initialGV.setCurrentState(automaton.getCurrentState());
        initialGV.setStates(initialStates);
        automaton.advance(initialGV);
        initialGV.setCurrentState(automaton.getCurrentState());
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
            automaton.advance(gv);
            if (automaton.getCurrentState() != gv.getCurrentState()) {
                // TODO: set gvn.currentState to automaton state with constructor
                GlobalView gvn = new GlobalView();
                GV.add(gvn);
                if (automaton.getEvaluation() == Automaton.Evaluation.SATISFIED) {
                    Log.d("processEvent", "I am satisfied!");
                } else if (automaton.getEvaluation() == Automaton.Evaluation.VIOLATED) {
                    Log.d("processEvent", "I feel violated!");
                }
            }
        }
        checkOutgoingTransitions(gv);
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
                if (trans.getEvaluation() == AutomatonTransition.Evaluation.TRUE) {
                    gv.getPendingTransitions().remove(trans);
                    GlobalView gvn = new GlobalView();
                    gvn.setCurrentState(trans.getTo());
                    GV.add(gvn);
                    if (Automaton.getEvalForState(gvn.getCurrentState()) == Automaton.Evaluation.SATISFIED) {
                        Log.d("processEvent", "I am satisfied!");
                    } else if (Automaton.getEvalForState(gvn.getCurrentState()) == Automaton.Evaluation.VIOLATED) {
                        Log.d("processEvent", "I feel violated!");
                    }
                    processEvent(gvn, gvn.getPendingEvents().remove());
                    if (gv.getPendingTransitions().isEmpty()) {
                        GV.remove(gv);
                    }
                } else if (trans.getEvaluation() == AutomatonTransition.Evaluation.FALSE) {
                    gv.getPendingTransitions().remove(trans);
                    if (gv.getPendingTransitions().isEmpty()) {
                        boolean hasEnabled = false;
                        for (AutomatonTransition gvTrans : gv.getPendingTransitions()) {
                            if (gvTrans.getEvaluation() == AutomatonTransition.Evaluation.TRUE) {
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
                }
            }
        } else {
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
            evaluateToken(token);
        } else {
            for (AutomatonTransition trans : token.getAutomatonTransitions()) {
                trans = trans.evaluate(AutomatonTransition.Evaluation.FALSE);
            }
            send(token, token.getOwner());
        }
    }

    /*
     * Evaluates each of token's predicates.
     *
     * @param token The token whose predicates will be evaluated.
     */
    public static void evaluateToken(Token token) {
        if (token.satisfiesAnyPredicate()) {
            send(token, token.getOwner());
        } else {
            waitingTokens.add(token.waitForNextEvent());
        }
    }
}
