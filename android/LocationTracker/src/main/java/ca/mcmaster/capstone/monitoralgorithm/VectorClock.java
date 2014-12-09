package ca.mcmaster.capstone.monitoralgorithm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/* Class to represent a vector clock.*/
public class VectorClock implements Iterable<Integer> {
    public static enum Comparison { EQUAL, BIGGER, SMALLER, CONCURRENT };

    private final List<Integer> consistentCut = new ArrayList<>();

    /* This constructor should eventually be deleted once things are fully implemented */
    public VectorClock() {
    }

    public VectorClock(final VectorClock vc) {
        this.consistentCut.addAll(vc.consistentCut);
    }

    /*
     * Constructs a vector clock from a List of Integers.
     *
     * @param consistentCut A List of Integers representing a consistent cut of all known processes.
     */
    public VectorClock(final List<Integer> consistentCut) {
        this.consistentCut.addAll(consistentCut);
    }

    /*
     * Compare two vector clocks.
     *
     * @param clock The clock to compare this to.
     * @return EQUAL if both clock are the same, BIGGER if this has a more recent event, SMALLER
     *         if clock has a more recent event, CONCURRENT each has some more recent events.
     * @throws IllegalArgumentException If the clocks are of different size this exception is thrown.
     */
    public Comparison compareToClock(final VectorClock clock) {
        if (this.size() != clock.size()) {
            throw new IllegalArgumentException("Clock passed to merge must match size of caller.");
        }

        boolean bigger = false;
        boolean smaller = false;
        for (int i = 0; i < this.size(); ++i) {
            if (this.process(i) > clock.process(i)) {
                bigger = true;
            } else if (this.process(i) < clock.process(i)) {
                smaller = true;
            }
        }

        if (bigger) {
            return Comparison.BIGGER;
        } else if (smaller) {
            return Comparison.SMALLER;
        } else if (bigger && smaller) {
            return Comparison.CONCURRENT;
        }
        return Comparison.EQUAL;
    }

    public Iterator<Integer> iterator() {
        return consistentCut.iterator();
    }

    /*
     * Merge two vector clocks so that that merged clock contains the most recently occurring events
     * in each process represented by the vector clocks.
     *
     * @param clock The clock to merge with the caller.
     * @return this merged with clock such that the new clock contains the most recent events from
     *         each clock being merged.
     * @throws IllegalArgumentException If the clocks are of different size this exception is thrown.
     */
    public VectorClock merge(VectorClock clock) {
        if (this.size() != clock.size()) {
            throw new IllegalArgumentException("Clock passed to merge must match size of caller.");
        }

        List merged = new ArrayList<Integer>();
        for (int i = 0; i < consistentCut.size(); ++i) {
            if (this.process(i) > clock.process(i)) {
                merged.add(new Integer(this.process(i)));
            } else {
                merged.add(new Integer(clock.process(i)));
            }
        }

        return new VectorClock(merged);
    }

    /*
     * Get the clock for the ith process kept in this VectorClock.
     *
     * @param i The index of the process whose clock to return.
     * @return The clock for the ith process.
     */
    public int process(final int i) {
        return consistentCut.get(i);
    }

    /*
     * Returns the number of processes represented in the VectorClock.
     *
     * @return The number of processes represented in this VectorClock.
     */
    public int size() {
        return consistentCut.size();
    }
}
