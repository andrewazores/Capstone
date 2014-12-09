package ca.mcmaster.capstone.monitoralgorithm;

import java.util.Arrays;

public class ArityException extends IllegalArgumentException {
    public ArityException(final int expected, final Object[] args) {
        super(String.format("Arity mismatch: expected %d arguments, got %d: %s", expected, args.length, Arrays.toString(args)));
    }
}
