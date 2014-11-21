package ca.mcmaster.capstone.monitoralgorithm;

import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    public AutomatonTransition(AutomatonState from, AutomatonState to, List<Conjunct> conjuncts) {
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
        return conjuncts;
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

    // XXX: probably not needed now
    public AutomatonTransition evaluate(Conjunct.Evaluation eval) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Set<Integer> getParticipatingProcesses() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public Set<Integer> getForbiddingProcesses(GlobalView gv) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}