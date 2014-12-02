package ca.mcmaster.capstone.monitoralgorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;

/* Class to represent the local view of the global state.*/
public class GlobalView {
    private final Map<HashableNsdServiceInfo, ProcessState> states = new HashMap<>();
    private VectorClock cut;
    private AutomatonState currentState;
    private final Set<Token> tokens = new HashSet<>();
    private final Queue<Event> pendingEvents = new ArrayDeque<>();
    private final Set<AutomatonTransition> pendingTransitions = new HashSet<>();

    public GlobalView() {
        //TODO: construct correct default objects
        //this.cut = new VectorClock();
        this.currentState = new AutomatonState("", Automaton.Evaluation.UNDECIDED);
    }

    public GlobalView(final GlobalView gv) {
        this.states.putAll(gv.states);
        this.cut = new VectorClock(gv.cut);
        this.currentState = new AutomatonState(gv.currentState);
        this.pendingEvents.addAll(gv.pendingEvents);
        this.pendingTransitions.addAll(gv.pendingTransitions);
    }

    public Map<HashableNsdServiceInfo, ProcessState> getStates() {
        return states;
    }

    public void setStates(final Map<HashableNsdServiceInfo, ProcessState> states) {
        this.states.clear();
        this.states.putAll(states);
    }

    public VectorClock getCut() {
        return cut;
    }

    public void setCut(final VectorClock cut) {
        this.cut = cut;
    }

    public AutomatonState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(final AutomatonState state) {
        this.currentState = state;
    }

    public Set<Token> getTokens() {
        return tokens;
    }

    public void setTokens(final List<Token> tokens) {
        this.tokens.clear();
        this.tokens.addAll(tokens);
    }

    public Queue<Event> getPendingEvents() {
        return pendingEvents;
    }

    public List<AutomatonTransition> getPendingTransitions() {
        return new ArrayList<>(pendingTransitions);
    }

    /*
     * Updates the global view with token.
     *
     * @param token The token to use to update the global view.
     */
    public void update(final Token token) {
        cut = cut.merge(token.getCut());
        states.put(token.getTargetProcessState().getId(), token.getTargetProcessState());
        // If any pending transitions are also in the token, and are evaluated in the token, remove them
        for (Iterator<AutomatonTransition> it = pendingTransitions.iterator(); it.hasNext();) {
            final AutomatonTransition pending = it.next();
            if (token.getAutomatonTransitions().contains(pending) && pending.evaluate(this.states.values()) != Conjunct.Evaluation.NONE) {
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
            //TODO: I'm pretty sure this is wrong.
            //consistent &= this.states.get(i).getId() == this.cut.process(i);
        }
        return consistent;
    }

    /*
     * Finds and returns a reference to the token that has the most conjuncts requested to be evaluated.
     *
     * @return A reference to The token with the most conjuncts requested to be evaluated.
     */
    public Token getTokenWithMostConjuncts() {
        Token ret = null;
        for (final Token token : this.tokens) {
            if (ret == null) {
                ret = token;
                break;
            }
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
        final List<Token> ret = new ArrayList<>();
        final List<Conjunct> transConjuncts = transition.getConjuncts();
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
    public Set<HashableNsdServiceInfo> getInconsistentProcesses() {
        final Set<HashableNsdServiceInfo> ret = new HashSet<>();
        for (final Map.Entry<HashableNsdServiceInfo, ProcessState> entry : this.states.entrySet()) {
            ProcessState state = entry.getValue();
            if (this.cut.compareToClock(state.getVC()) == VectorClock.Comparison.BIGGER ||
                    this.cut.compareToClock(state.getVC()) == VectorClock.Comparison.SMALLER) {
                ret.add(state.getId());
            }
        }
        return ret;
    }

    /*
     * Attempts to merge this GlobalView with gv by treating the members of each as sets,
     * and taking the union.
     *
     * @param gv The GlobalView to merge with this one.
     * @return Returns a new GlobalView if this can be merged with gv, null otherwise.
     */
    public GlobalView merge(GlobalView gv) {
        VectorClock.Comparison compare = this.cut.compareToClock(gv.cut);
        if (this.currentState == gv.currentState &&
                (compare == VectorClock.Comparison.CONCURRENT || compare == VectorClock.Comparison.EQUAL)) {
            GlobalView ret = new GlobalView();
            //XXX: I'm not sure that these are guaranteed to be the same. We may be losing information here.
            //     For example, what happens if the states in gv differ from those in this object?
            ret.states.putAll(this.states);
            ret.states.putAll(gv.states);
            ret.cut = this.cut.merge(gv.cut);
            ret.currentState = this.currentState;
            ret.tokens.addAll(unionMerge(this.tokens, gv.tokens));
            ret.pendingEvents.addAll(unionMerge(this.pendingEvents, gv.pendingEvents));
            ret.pendingTransitions.addAll(unionMerge(this.pendingTransitions, gv.pendingTransitions));
            return ret;
        }
        return null;
    }

    private static <T> Set<T> unionMerge(final Collection<T> first, final Collection<T> second) {
        final Set<T> firstSet = new HashSet<>(first);
        final Set<T> secondSet = new HashSet<>(second);
        firstSet.addAll(secondSet);
        return firstSet;
    }
}