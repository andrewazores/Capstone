package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/* Class to represent the computation slicing tokens.*/
@EqualsAndHashCode @ToString
public class Token {
    public static class Builder {
        private final NetworkPeerIdentifier owner;
        private final NetworkPeerIdentifier destination;

        private int targetEventId = 0;
        private VectorClock cut;
        private final Set<AutomatonTransition> automatonTransitions = new HashSet<>();
        private final Map<Conjunct, Conjunct.Evaluation> conjuncts = new HashMap<>();
        private ProcessState targetProcessState = null;
        public boolean returned = false;
        public boolean sent = false;

        public Builder(final NetworkPeerIdentifier owner, final NetworkPeerIdentifier destination) {
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

    @NonNull @Getter private final NetworkPeerIdentifier owner;
    @NonNull @Getter private final NetworkPeerIdentifier destination;
    @Getter private final int targetEventId;
    @NonNull @Getter private final VectorClock cut;
    private final Set<AutomatonTransition> automatonTransitions = new HashSet<>();
    private final Map<Conjunct, Conjunct.Evaluation> conjuncts = new HashMap<>();
    @NonNull @Getter private final ProcessState targetProcessState;
    @Setter private boolean returned = false;
    @Setter private boolean sent = false;

    public Token(@NonNull final Builder builder) {
        this.owner = builder.owner;
        this.destination = builder.destination;
        this.targetEventId = builder.targetEventId;
        this.cut = builder.cut;
        this.automatonTransitions.addAll(builder.automatonTransitions);
        this.conjuncts.putAll(builder.conjuncts);
        this.targetProcessState = builder.targetProcessState;
    }

    public Set<Conjunct> getConjuncts() {
        return new HashSet<>(conjuncts.keySet());
    }

    public Map<Conjunct, Conjunct.Evaluation> getConjunctsMap() {
        return new HashMap<>(conjuncts);
    }

    public Set<AutomatonTransition> getAutomatonTransitions() {
        return new HashSet<>(automatonTransitions);
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
    public void evaluateConjuncts(@NonNull final Event event) {
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
