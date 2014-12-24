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

import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.NonNull;
import lombok.ToString;

/* Class to represent the local view of the global state.*/
@ToString
public class GlobalView {
    private final Map<NetworkPeerIdentifier, ProcessState> states = new HashMap<>();
    @NonNull private VectorClock cut;
    @NonNull private AutomatonState currentState;
    private final Set<Token> tokens = new HashSet<>();
    private final Queue<Event> pendingEvents = new ArrayDeque<>();
    private final Set<AutomatonTransition> pendingTransitions = new HashSet<>();

    public GlobalView() {
        //TODO: construct correct default objects
        //this.cut = new VectorClock();
        this.currentState = new AutomatonState("", Automaton.Evaluation.UNDECIDED);
    }

    public GlobalView(@NonNull final GlobalView gv) {
        this.states.putAll(gv.states);
        this.cut = new VectorClock(gv.cut);
        this.currentState = new AutomatonState(gv.currentState);
        this.pendingEvents.addAll(gv.pendingEvents);
        this.pendingTransitions.addAll(gv.pendingTransitions);
    }

    public Map<NetworkPeerIdentifier, ProcessState> getStates() {
        return new HashMap<>(states);
    }

    public void setStates(@NonNull final Map<NetworkPeerIdentifier, ProcessState> states) {
        this.states.clear();
        this.states.putAll(states);
    }

    public VectorClock getCut() {
        return cut;
    }

    public void setCut(@NonNull final VectorClock cut) {
        this.cut = cut;
    }

    public AutomatonState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(@NonNull final AutomatonState state) {
        this.currentState = state;
    }

    public Set<Token> getTokens() {
        return tokens;
    }

    public void setTokens(@NonNull final List<Token> tokens) {
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
    public void update(@NonNull final Token token) {
        cut = cut.merge(token.getCut());
        states.put(token.getTargetProcessState().getId(), token.getTargetProcessState());
        // If any pending transitions are also in the token, and are evaluated in the token, remove them
        for (final Iterator<AutomatonTransition> it = pendingTransitions.iterator(); it.hasNext();) {
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
     * Finds the token that has the most conjuncts requested to be evaluated.
     *
     * @return A reference to The token with the most conjuncts requested to be evaluated, or null if
     *         there are no tokens in this global view.
     */
    public Token getTokenWithMostConjuncts() {
        Token ret = null;
        for (final Token token : this.tokens) {
            if (ret == null || token.getConjuncts().size() > ret.getConjuncts().size()) {
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
    public List<Token> getTokensForTransition(@NonNull final AutomatonTransition transition) {
        final List<Token> ret = new ArrayList<>();
        final List<Conjunct> transConjuncts = transition.getConjuncts();
        for (final Token token : this.tokens) {
            for (final Conjunct conjunct : token.getConjuncts()) {
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
    public Set<NetworkPeerIdentifier> getInconsistentProcesses() {
        final Set<NetworkPeerIdentifier> ret = new HashSet<>();
        for (final Map.Entry<NetworkPeerIdentifier, ProcessState> entry : this.states.entrySet()) {
            final ProcessState state = entry.getValue();
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
    public GlobalView merge(@NonNull final GlobalView gv) {
        final VectorClock.Comparison compare = this.cut.compareToClock(gv.cut);
        if (this.currentState == gv.currentState &&
                (compare == VectorClock.Comparison.CONCURRENT || compare == VectorClock.Comparison.EQUAL)) {
            final GlobalView ret = new GlobalView();
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

    private static <T> Set<T> unionMerge(@NonNull final Collection<T> first, @NonNull final Collection<T> second) {
        final Set<T> firstSet = new HashSet<>(first);
        final Set<T> secondSet = new HashSet<>(second);
        firstSet.addAll(secondSet);
        return firstSet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GlobalView that = (GlobalView) o;

        if (!currentState.equals(that.currentState)) return false;
        if (!cut.equals(that.cut)) return false;
        if (!pendingEvents.equals(that.pendingEvents)) return false;
        if (!pendingTransitions.equals(that.pendingTransitions)) return false;
        if (!states.equals(that.states)) return false;
        if (!tokens.equals(that.tokens)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = states.hashCode();
        result = 31 * result + cut.hashCode();
        result = 31 * result + currentState.hashCode();
        result = 31 * result + tokens.hashCode();
        result = 31 * result + pendingEvents.hashCode();
        result = 31 * result + pendingTransitions.hashCode();
        return result;
    }
}