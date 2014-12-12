package ca.mcmaster.capstone.monitoralgorithm;

import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;

public final class Event {
    public static enum EventType {SEND, RECEIVE, INTERNAL}

    private final int eid;
    private final HashableNsdServiceInfo pid;
    private final EventType type;
    private final Valuation<?> val;
    private final VectorClock VC;

    public Event(final int eid, final HashableNsdServiceInfo pid, final EventType type, final Valuation<?> val, final VectorClock VC) {
        this.eid = eid;
        this.pid = pid;
        this.type = type;
        this.val = val;
        this.VC = VC;
    }

    public int eid() {
        return eid;
    }

    public HashableNsdServiceInfo pid() {
        return pid;
    }

    public EventType getType() {
        return type;
    }

    public Valuation<?> getVal() {
        return val;
    }

    public VectorClock getVC() {
        return VC;
    }

    /*
         * Returns a ProcessState based on this event's vector clock and variable valuation.
         *
         * @return A new ProcessState based on this event's vector clock and variable valuation.
         */
    public ProcessState getState() {
        return new ProcessState(this.pid, this.val, this.VC);
    }

    @Override
    public String toString() {
        return "Event{" +
                "eid=" + eid +
                ", pid=" + pid +
                ", type=" + type +
                ", val=" + val +
                ", VC=" + VC +
                '}';
    }
}
