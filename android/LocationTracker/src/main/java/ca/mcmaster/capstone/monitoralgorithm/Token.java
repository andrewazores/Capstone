package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* Class to represent the computation slicing tokens.*/
public class Token {
    public static class Builder {
        private final int owner;
        private final int destination;

        private int targetEventId = 0;
        private VectorClock cut = new VectorClock();
        private final Set<AutomatonTransition> automatonTransitions = new HashSet<>();
        private final Map<Conjunct, Conjunct.Evaluation> conjuncts = new HashMap<>();
        private ProcessState targetProcessState = null;
        public boolean returned = false;
        public boolean sent = false;

        public Builder(final int owner, final int destination) {
            this.owner = owner;
            this.destination = destination;
        }

        public Builder(final Token token) {
            this.owner = token.owner;
            this.destination = token.destination;
            this.targetEventId = token.targetEventId;
            this.cut = new VectorClock(token.cut);
            this.automatonTransitions.addAll(token.automatonTransitions);
            this.conjuncts.putAll(token.conjuncts);
            this.targetProcessState = new ProcessState(token.targetProcessState);
            this.returned = token.returned;
            this.sent = token.sent;
        }

        public Builder targetEventId(final int id) {
            this.targetEventId = id;
            return this;
        }

        public Builder cut(final VectorClock cut) {
            this.cut = new VectorClock(cut);
            return this;
        }

        public Builder automatonTransitions(final Set<AutomatonTransition> transitions) {
            this.automatonTransitions.addAll(transitions);
            return this;
        }

        public Builder conjuncts(final Map<Conjunct, Conjunct.Evaluation> conjuncts) {
            this.conjuncts.putAll(conjuncts);
            return this;
        }

        public Builder targetProcessState(final ProcessState state) {
            this.targetProcessState = new ProcessState(state);
            return this;
        }

        public Builder returned(final boolean returned) {
            this.returned = returned;
            return this;
        }

        public Builder sent(final boolean sent) {
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
    private final Map<Conjunct, Conjunct.Evaluation> conjuncts = new HashMap<>();
    private final ProcessState targetProcessState;
    private final boolean returned = false;
    private final boolean sent = false;

    public Token(final Builder builder) {
        this.owner = builder.owner;
        this.destination = builder.destination;
        this.targetEventId = builder.targetEventId;
        this.cut = builder.cut;
        this.automatonTransitions.addAll(builder.automatonTransitions);
        this.conjuncts.putAll(builder.conjuncts);
        this.targetProcessState = builder.targetProcessState;
    }

    public int getOwner() {
        return owner;
    }

    public Set<Conjunct> getConjuncts() {
        return new HashSet<>(conjuncts.keySet());
    }

    public Map<Conjunct, Conjunct.Evaluation> getConjunctsMap() {
        return new HashMap<>(conjuncts);
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
        return new HashSet<>(automatonTransitions);
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
    public void evaluateConjuncts(final Event event) {
        for (final Conjunct conjunct : conjuncts.keySet()) {
            conjuncts.put(conjunct, conjunct.evaluate(event.getState()));
        }
    }

    /*
     * Checks if any of this token's conjuncts are satisfied.
     *
     * @return True if at least one conjunct is satisfied, false otherwise.
     */
    public boolean anyConjunctSatisfied() {
        return conjuncts.containsValue(Conjunct.Evaluation.TRUE);
    }
}
