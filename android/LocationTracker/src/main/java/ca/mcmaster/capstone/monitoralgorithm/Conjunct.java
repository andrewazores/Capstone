package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashSet;
import java.util.Set;

import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;

public class Conjunct {
    public static enum Evaluation {TRUE, FALSE, NONE}

    private final Set<HashableNsdServiceInfo> ownerProcesses = new HashSet<>();
    private final BooleanExpressionTree expression = new BooleanExpressionTree();

    public Conjunct(final Set<HashableNsdServiceInfo> ownerProcesses) {
        this.ownerProcesses.addAll(ownerProcesses);
    }

    public Set<HashableNsdServiceInfo> getOwnerProcesses() {
        return new HashSet<>(ownerProcesses);
    }

    public Evaluation evaluate(final ProcessState state) {
        return expression.evaluate(state);
    }
}
