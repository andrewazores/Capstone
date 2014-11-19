package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/* Class to represent the computation slicing tokens.*/
public class Token {
    private final int owner;
    private final int destination;
    private final int targetEventId;
    private final VectorClock cut;
    private final Set<AutomatonTransition> automatonTransitions = new HashSet<>();
    private final Set<Conjunct> conjuncts = new HashSet<>();
    private ProcessState targetProcessState;

    public boolean returned = false;
    public boolean sent = false;

    public Token(int owner, int dest, int targetEventId, VectorClock cut, Set<AutomatonTransition> trans,
                 Set<Conjunct> conjuncts, ProcessState state) {
        // Place holder initializations for stub implementation.
        this.owner = owner;
        this.destination = dest;
        this.targetEventId = targetEventId;
        this.cut = cut;
        this.automatonTransitions.addAll(trans);
        this.conjuncts.addAll(conjuncts);
        this.targetProcessState = state;
    }

    public int getOwner() {
        return owner;
    }

    public Set<Conjunct> getConjuncts() {
        return conjuncts;
    }

    public int getDestination() {
        return destination;
    }

    public int getTargetEventId() {
        return targetEventId;
    }

    public VectorClock getCut() {
        return cut;
    }

    public Set<AutomatonTransition> getAutomatonTransitions() {
        return automatonTransitions;
    }

    public ProcessState getTargetProcessState() {
        return targetProcessState;
    }

    public void setTargetProcessState(ProcessState targetProcessState) {
        this.targetProcessState = targetProcessState;
    }

    /*
     * Returns a token with its target event increased by 1.
     *
     * @return A new token with its target event increased by 1.
     */
    public Token waitForNextEvent() {
        return new Token(this.owner, this.destination, this.targetEventId + 1, this.cut, this.automatonTransitions,
                this.conjuncts, this.targetProcessState);
    }

    /*
     * Uses the state information in event to evaluate this token's conjuncts.
     *
     * @param event The event to use to evaluate the transitions.
     */
    public void evaluateConjuncts(Event event){
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /*
     * Checks if any of this token's conjuncts are satisfied.
     *
     * @return True if at least one conjunct is satisfied, false otherwise.
     */
    public boolean anyConjunctSatisfied() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
