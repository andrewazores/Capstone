package ca.mcmaster.capstone.monitoralgorithm;

import java.util.ArrayList;
import java.util.List;

/* Class to represent a vector clock.*/
public class VectorClock {
    public static enum Comparison { EQUAL, FIRST_BIGGER, FIRST_SMALLER, CONCURRENT };

    private List<Integer> consistentCut;

    public VectorClock() {
        this.consistentCut = new ArrayList<Integer>();
    }

    public VectorClock merge(VectorClock clock) {
        // Place holder
        return new VectorClock();

    }

    public Comparison compareToClock(VectorClock clock) {
        // Place holder
        return Comparison.CONCURRENT;
    }
}
