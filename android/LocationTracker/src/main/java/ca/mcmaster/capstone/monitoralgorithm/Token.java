package ca.mcmaster.capstone.monitoralgorithm;

import android.os.Build;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/* Class to represent the computation slicing tokens.*/
public class Token {
    public static class Builder {
        private final int owner;
        private final int destination;

        private int targetEventId = 0;
        private VectorClock cut = new VectorClock();
        private Set<AutomatonTransition> automatonTransitions = new HashSet<>();
        private Set<Conjunct> conjuncts = new HashSet<>();
        private ProcessState targetProcessState = null;
        public boolean returned = false;
        public boolean sent = false;

        public Builder(int owner, int destination) {
            this.owner = owner;
            this.destination = destination;
        }

        public Builder(Token token) {
            this.owner = token.owner;
            this.destination = token.destination;
            this.targetEventId = token.targetEventId;
            this.cut = new VectorClock(token.cut);
            this.automatonTransitions.addAll(token.automatonTransitions);
            this.conjuncts.addAll(token.conjuncts);
            this.targetProcessState = new ProcessState(token.targetProcessState);
            this.returned = token.returned;
            this.sent = token.sent;
        }

        public Builder targetEventId(int id) {
            this.targetEventId = id;
            return this;
        }

        public Builder cut(VectorClock cut) {
            this.cut = new VectorClock(cut);
            return this;
        }

        public Builder automatonTransitions(Set<AutomatonTransition> transitions) {
            this.automatonTransitions.addAll(transitions);
            return this;
        }

        public Builder conjuncts(Set<Conjunct> conjuncts) {
            this.conjuncts.addAll(conjuncts);
            return this;
        }

        public Builder targetProcessState(ProcessState state) {
            this.targetProcessState = new ProcessState(state);
            return this;
        }

        public Builder returned(boolean returned) {
            this.returned = returned;
            return this;
        }

        public Builder sent(boolean sent) {
            this.sent = sent;
            return this;
        }

        public Token build() {
            return new Token(this);
        }
    }

    private final int owner;
    private final int destination;
    private final int targetEventId;
    private final VectorClock cut;
    private final Set<AutomatonTransition> automatonTransitions = new HashSet<>();
    private final Set<Conjunct> conjuncts = new HashSet<>();
    private final ProcessState targetProcessState;
    private final boolean returned = false;
    private final boolean sent = false;

    public Token(Builder builder) {
        this.owner = builder.owner;
        this.destination = builder.destination;
        this.targetEventId = builder.targetEventId;
        this.cut = builder.cut;
        this.automatonTransitions.addAll(builder.automatonTransitions);
        this.conjuncts.addAll(builder.conjuncts);
        this.targetProcessState = builder.targetProcessState;
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

    /*
     * Returns a token with its target event increased by 1.
     *
     * @return A new token with its target event increased by 1.
     */
    public Token waitForNextEvent() {
        return new Builder(this).targetEventId(this.targetEventId + 1).build();
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
