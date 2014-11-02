package ca.mcmaster.capstone.monitoralgorithm;

/* Class to represent an automaton transition.*/
public class AutomatonTransition {
    public static enum Evaluation {TRUE, FALSE, NONE}

    private AutomatonState from;
    private AutomatonState to;
    private BooleanExpressionTree predicate;
    private Evaluation evaluation;

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