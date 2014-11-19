package ca.mcmaster.capstone.monitoralgorithm;

public class Conjunct {
    public static enum Evaluation {TRUE, FALSE, NONE}

    private Evaluation evaluation = Evaluation.NONE;

    public Evaluation getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(Evaluation evaluation) {
        this.evaluation = evaluation;
    }
}
