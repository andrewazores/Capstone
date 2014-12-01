package ca.mcmaster.capstone.monitoralgorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/* Class to represent the local view of the global state.*/
public class GlobalView {
    private List<ProcessState> states;
    private VectorClock cut;
    private AutomatonState currentState;
    private List<Token> tokens;
    private final Queue<Event> pendingEvents = new ArrayDeque<>();
    private final List<AutomatonTransition> pendingTransitions = new ArrayList<>();

    public GlobalView() {
        this.states = new ArrayList<ProcessState>();
        this.cut = new VectorClock();
        this.currentState = new AutomatonState("", Automaton.Evaluation.UNDECIDED);
        this.tokens = new ArrayList();
    }

    public GlobalView(GlobalView gv) {
        this.states.addAll(gv.getStates());
        this.cut = new VectorClock(gv.getCut());
        this.currentState = new AutomatonState(gv.getCurrentState());
        this.pendingEvents.addAll(gv.getPendingEvents());
        this.pendingTransitions.addAll(gv.getPendingTransitions());
    }

    public List<ProcessState> getStates() {
        return states;
    }

    public void setStates(List<ProcessState> states) {
        this.states = states;
    }

    public VectorClock getCut() {
        return cut;
    }

    public void setCut(VectorClock cut) {
        this.cut = cut;
    }

    public AutomatonState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(AutomatonState state) {
        this.currentState = state;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public void setTokens(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Queue<Event> getPendingEvents() {
        return pendingEvents;
    }

    public List<AutomatonTransition> getPendingTransitions() {
        return pendingTransitions;
    }

    /*
     * Updates the global view with token.
     *
     * @param token The token to use to update the global view.
     */
    public void update(Token token) {
        cut = cut.merge(token.getCut());
        states.set(token.getTargetProcessState().getId(), token.getTargetProcessState());
        // If any pending transitions are also in the token, and are evaluated in the token, remove them
        for (Iterator<AutomatonTransition> it = pendingTransitions.iterator(); it.hasNext();) {
            AutomatonTransition pending = it.next();
            if (token.getAutomatonTransitions().contains(pending) && pending.getEvaluation() != Conjunct.Evaluation.NONE) {
                it.remove();
            }
        }
    }

    /*
     * Checks if the GlobalView is consistent for all known processes.
     *
     * @return True if the GlobalView is consistent, False if not.
     */
    public boolean isConsistent() {
        boolean consistent = true;
        for (int i = 0; i < cut.size(); ++i) {
            consistent &= this.states.get(i).getId() == this.cut.process(i);
        }
        return consistent;
    }

    /*
     * Finds and returns a reference to the token that has the most conjuncts requested to be evaluated.
     *
     * @return A reference to The token with the most conjuncts requested to be evaluated.
     */
    public Token getTokenWithMostConjuncts() {
        Token ret = this.tokens.get(0);
        for (Token token : this.tokens) {
            if (token.getConjuncts().size() > ret.getConjuncts().size()) {
                ret = token;
            }
        }
        return ret;
    }

    /*
     * Gets all tokens in the global view that are associated with a particular transition.
     *
     * @param transition The transition to match tokens against.
     * @return A list of tokens which are associated with transition.
     */
    public List<Token> getTokensForTransition(AutomatonTransition transition) {
        List<Token> ret = new ArrayList<>();
        List<Conjunct> transConjuncts = transition.getConjuncts();
        for (Token token : this.tokens) {
            for (Conjunct conjunct : token.getConjuncts()) {
                if (transConjuncts.contains(conjunct)) {
                    ret.add(token);
                    break;
                }
            }
        }
        return ret;
    }

    /*
     * Returns a set of process ids for processes that are inconsistent with the local vector clock.
     * A process is inconsistent if it's vector clock is not equal to or concurrent with this process'.
     *
     * @return A set of process ids, for inconsistent processes.
     */
    public Set<Integer> getInconsistentProcesses() {
        Set<Integer> ret = new HashSet<>();
        for (ProcessState state : this.states) {
            if (this.cut.compareToClock(state.getVC()) == VectorClock.Comparison.BIGGER ||
                    this.cut.compareToClock(state.getVC()) == VectorClock.Comparison.SMALLER) {
                ret.add(state.getId());
            }
        }
        return ret;
    }
}