package ca.mcmaster.capstone.monitoralgorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;

/* Class to represent an automaton transition.*/
public class AutomatonTransition {
    private AutomatonState from;
    private AutomatonState to;
    private List<Conjunct> conjuncts = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AutomatonTransition that = (AutomatonTransition) o;

        if (!conjuncts.equals(that.conjuncts)) return false;
        if (from != null ? !from.equals(that.from) : that.from != null) return false;
        if (to != null ? !to.equals(that.to) : that.to != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = from != null ? from.hashCode() : 0;
        result = 31 * result + (to != null ? to.hashCode() : 0);
        result = 31 * result + conjuncts.hashCode();
        return result;
    }

    public AutomatonTransition(final AutomatonState from, final AutomatonState to, final List<Conjunct> conjuncts) {
        this.from = from;
        this.to = to;
        this.conjuncts.addAll(conjuncts);
    }

    public AutomatonState getTo() {
        return to;
    }

    public AutomatonState getFrom() {
        return from;
    }

    public List<Conjunct> getConjuncts() {
        return new ArrayList<>(conjuncts);
    }

    /*
     * Computes the evaluation of the transition based on the evaluation of each conjunct.
     *
     * @return The evaluation of the transition based on its conjuncts.
     */
    public Conjunct.Evaluation getEvaluation() {
        if (this.conjuncts.contains(Conjunct.Evaluation.FALSE)) {
            return Conjunct.Evaluation.FALSE;
        } else if (this.conjuncts.contains(Conjunct.Evaluation.NONE)) {
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
    public Set<HashableNsdServiceInfo> getParticipatingProcesses() {
        final Set<HashableNsdServiceInfo> ret = new HashSet<>();
        for (final Conjunct conjunct : conjuncts) {
            ret.addAll(conjunct.getOwnerProcesses());
        }
        return ret;
    }

    /*
     * Returns a set of process ids for the processes that cause this transition to evaluate to false.
     *
     * @return A set of process ids.
     */
    public Set<HashableNsdServiceInfo> getForbiddingProcesses(final GlobalView gv) {
        final Set<HashableNsdServiceInfo> ret = new HashSet<>();
        for (final Map.Entry<HashableNsdServiceInfo, ProcessState> entry : gv.getStates().entrySet()) {
            ProcessState state = entry.getValue();
            for (final Conjunct conjunct : conjuncts) {
                if (conjunct.evaluate(state) == Conjunct.Evaluation.FALSE) {
                    ret.add(state.getId());
                    break;
                }
            }
        }
        return ret;
    }

    public boolean enabled(final List<Token> tokens) throws Exception {
        Map<Conjunct, Conjunct.Evaluation> enabled = new HashMap<>();
        for (Token token : tokens) {
            for (Conjunct conjunct : token.getConjuncts()) {
                if (this.conjuncts.contains(conjunct)) {
                    enabled.put(conjunct, token.getConjunctsMap().get(conjunct));
                }
            }
        }
        if (enabled.values().contains(Conjunct.Evaluation.NONE)) {
            throw new Exception("Found an unevaluated conjunct where all should be evaluated.");
        }
        if (enabled.values().contains(Conjunct.Evaluation.FALSE)) {
            return false;
        }
        return true;
    }
}