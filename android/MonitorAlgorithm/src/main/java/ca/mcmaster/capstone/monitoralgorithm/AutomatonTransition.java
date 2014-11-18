/* Class to represent an automaton transition.*/
public class AutomatonTransition {
    public static enum Evaluation {TRUE, FALSE, NONE}

    private AutomatonState from;
    private AutomatonState to;
    private BooleanExpressionTree predicate;
    private Evaluation evaluation;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AutomatonTransition that = (AutomatonTransition) o;

        if (from != null ? !from.equals(that.from) : that.from != null) return false;
        if (predicate != null ? !predicate.equals(that.predicate) : that.predicate != null)
            return false;
        if (to != null ? !to.equals(that.to) : that.to != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = from != null ? from.hashCode() : 0;
        result = 31 * result + (to != null ? to.hashCode() : 0);
        result = 31 * result + (predicate != null ? predicate.hashCode() : 0);
        return result;
    }

    public AutomatonTransition(AutomatonState from, AutomatonState to, BooleanExpressionTree predicate, Evaluation eval) {
        this.from = from;
        this.to = to;
        this.predicate = predicate;
        this.evaluation = eval;
    }

    public BooleanExpressionTree getPredicate() {
        return predicate;
    }

    public AutomatonState getTo() {
        return to;
    }

    public AutomatonState getFrom() {
        return from;
    }

    public Evaluation getEvaluation() {
        return evaluation;
    }

    public AutomatonTransition evaluate(Evaluation eval) {
        return new AutomatonTransition(this.from, this.to, this.predicate, eval);
    }
}