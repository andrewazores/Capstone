package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashSet;
import java.util.Set;

public class Conjunct {
    public static enum Evaluation {TRUE, FALSE, NONE}

    private final Set<Integer> ownerProcesses = new HashSet<>();

    public Conjunct(final Set<Integer> ownerProcesses) {
        this.ownerProcesses.addAll(ownerProcesses);
    }

    public Set<Integer> getOwnerProcesses() {
        return new HashSet<>(ownerProcesses);
    }

    public Evaluation evaluate(final ProcessState state) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
