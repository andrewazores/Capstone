package ca.mcmaster.capstone.monitoralgorithm;

import java.util.ArrayList;
import java.util.List;

/* Class to represent a vector clock.*/
public class VectorClock {
    private List<Integer> consistentCut;

    public VectorClock() {
        this.consistentCut = new ArrayList<Integer>();
    }

    public void merge(VectorClock clock) {

    }

    @Override
    public boolean equals(Object o) {
        return false;
    }
}
