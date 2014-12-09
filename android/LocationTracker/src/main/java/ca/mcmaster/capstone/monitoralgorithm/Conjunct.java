package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashSet;
import java.util.Set;

public class Conjunct {
    public static enum Evaluation {TRUE, FALSE, NONE}

    private final Set<Integer> ownerProcesses = new HashSet<>();
    private final BooleanExpressionTree expression = new BooleanExpressionTree();

    public Conjunct(final Set<Integer> ownerProcesses) {
        this.ownerProcesses.addAll(ownerProcesses);
    }

    public Set<Integer> getOwnerProcesses() {
        return new HashSet<>(ownerProcesses);
    }

    public Evaluation evaluate(final ProcessState state) {
        return expression.evaluate(state);
    }
}
