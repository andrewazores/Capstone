package ca.mcmaster.capstone.monitoralgorithm;

/* Class to represent an automaton transition.*/
public class AutomatonTransition {
    private enum Evaluation {TRUE, FALSE, NONE}

    private AutomatonState from;
    private AutomatonState to;
    private BooleanExpressionTree predicate;
    private Evaluation evaluation;

    public Evaluation getEvaluation() {
        return evaluation;
    }

    public AutomatonTransition(AutomatonState from, AutomatonState to, BooleanExpressionTree predicate) {
        this.from = from;
        this.to = to;
        this.predicate = predicate;
        this.evaluation = Evaluation.NONE;
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
}