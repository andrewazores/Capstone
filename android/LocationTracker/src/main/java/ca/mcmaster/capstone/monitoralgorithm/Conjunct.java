package ca.mcmaster.capstone.monitoralgorithm;

import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;

public class Conjunct {
    public static enum Evaluation {TRUE, FALSE, NONE}

    private final HashableNsdServiceInfo ownerProcess;
    private final BooleanExpressionTree expression;

    public Conjunct(final HashableNsdServiceInfo ownerProcess, String expression) {
        this.ownerProcess = ownerProcess;
        this.expression = new BooleanExpressionTree(expression);
    }

    public HashableNsdServiceInfo getOwnerProcess() {
        return ownerProcess;
    }

    public Evaluation evaluate(final ProcessState state) {
        return expression.evaluate(state);
    }
}
