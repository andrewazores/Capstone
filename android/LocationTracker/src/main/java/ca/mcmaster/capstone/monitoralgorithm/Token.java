package ca.mcmaster.capstone.monitoralgorithm;

import java.util.LinkedHashSet;
import java.util.Set;

/* Class to represent the computation slicing tokens.*/
public class Token {
    private final int owner;
    private final int destination;
    private final int targetEventId;
    private final VectorClock cut;
    private final Set<AutomatonTransition> automatonTransitions;
    private final ProcessState targetProcessState;

    public Token(int owner, int dest, int targetEventId, VectorClock cut, Set<AutomatonTransition> trans,
                 ProcessState state) {
        // Place holder initializations for stub implementation.
        this.owner = owner;
        this.destination = dest;
        this.targetEventId = targetEventId;
        this.cut = cut;
        this.automatonTransitions = trans;
        this.targetProcessState = state;
    }

    public int getOwner() {
        return owner;
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

    public boolean satisfiesAnyPredicate() {
        // Place holder
        return false;
    }

    ;

    public Token waitForNextEvent() {
        return new Token(this.owner, this.destination, this.targetEventId + 1, this.cut, this.automatonTransitions,
                this.targetProcessState);
    }
}
