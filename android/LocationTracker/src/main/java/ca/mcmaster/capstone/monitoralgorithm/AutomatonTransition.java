package ca.mcmaster.capstone.monitoralgorithm;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/* Class to represent an automaton transition.*/
@EqualsAndHashCode @ToString
public class AutomatonTransition {
    public static String LOG_TAG = "AutomatonTransition";

    @NonNull @Getter private final AutomatonState from;
    @NonNull @Getter private final AutomatonState to;
    private final List<Conjunct> conjuncts = new ArrayList<>();

    public AutomatonTransition(@NonNull final AutomatonState from, @NonNull final AutomatonState to, @NonNull final List<Conjunct> conjuncts) {
        this.from = from;
        this.to = to;
        this.conjuncts.addAll(conjuncts);
    }

    public List<Conjunct> getConjuncts() {
        return new ArrayList<>(conjuncts);
    }

    /*
     * Computes the evaluation of the transition based on the evaluation of each conjunct.
     *
     * @return The evaluation of the transition based on its conjuncts.
     * @throws EvaluationException
     */
    public Conjunct.Evaluation evaluate(@NonNull final Collection<ProcessState> processStates) throws EvaluationException {
        final Map<Conjunct, Conjunct.Evaluation> evaluations = new HashMap<>();
        for (final ProcessState state : processStates) {
            for (final Conjunct conjunct : this.conjuncts) {
                if (conjunct.getOwnerProcess().equals(state.getId())) {
                    evaluations.put(conjunct, conjunct.evaluate(state));
                }
            }
        }

        if (evaluations.isEmpty()) {
            throw new EvaluationException("No conjuncts were evaluated with this Collection of states: " + processStates.toString());
        }

        if (evaluations.values().contains(Conjunct.Evaluation.FALSE)) {
            return Conjunct.Evaluation.FALSE;
        } else if (evaluations.values().contains(Conjunct.Evaluation.NONE)) {
            return Conjunct.Evaluation.NONE;
        }
        return Conjunct.Evaluation.TRUE;
    }

    /*
     * Returns a set of process ids for the processes that contribute variables to the predicate
     * labeling this transition.
     *
     * @return A set of process ids.
     */
    public Set<NetworkPeerIdentifier> getParticipatingProcesses() {
        final Set<NetworkPeerIdentifier> ret = new HashSet<>();
        for (final Conjunct conjunct : conjuncts) {
            ret.add(conjunct.getOwnerProcess());
        }
        return ret;
    }

    /*
     * Returns a set of conjuncts that cause this transition to evaluate to false.
     *
     * @return A set of Conjuncts.
     */
    public Set<Conjunct> getForbiddingConjuncts(@NonNull final GlobalView gv) {
        final Set<Conjunct> ret = new HashSet<>();
        for (final Map.Entry<NetworkPeerIdentifier, ProcessState> entry : gv.getStates().entrySet()) {
            final ProcessState state = entry.getValue();
            for (final Conjunct conjunct : conjuncts) {
                if (conjunct.getOwnerProcess().equals(state.getId())
                        && conjunct.evaluate(state) == Conjunct.Evaluation.FALSE) {
                    ret.add(conjunct);
                    break;
                }
            }
        }
        return ret;
    }

    public boolean enabled(@NonNull GlobalView globalView, @NonNull final List<Token> tokens) {
        Map<NetworkPeerIdentifier, ProcessState> states = new HashMap<>(globalView.getStates());
        for (Token token : tokens) {
            final ProcessState targetProcessState = token.getTargetProcessState();
            states.put(targetProcessState.getId(), targetProcessState);
        }

        boolean evaluation = false;
        try {
            evaluation = this.evaluate(states.values()) == Conjunct.Evaluation.TRUE;
        } catch (EvaluationException e) {
            Log.e(LOG_TAG, e.getLocalizedMessage());
        }

        Log.d(LOG_TAG, "Transition: " + this + "\nevaluates to: " + evaluation);
        return evaluation;
    }

    public class EvaluationException extends Exception {
        public EvaluationException(@NonNull final String message) {
            super("Failed to evaluate: " + message);
        }
    }
}