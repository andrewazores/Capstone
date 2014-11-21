package ca.mcmaster.capstone.monitoralgorithm;

public class Conjunct {
    public static enum Evaluation {TRUE, FALSE, NONE}

    private Evaluation evaluation = Evaluation.NONE;
    private final int ownerProcess;

    public Conjunct(int ownerProcess) {
        this.ownerProcess = ownerProcess;
    }

    public Evaluation getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(Evaluation evaluation) {
        this.evaluation = evaluation;
    }
}
