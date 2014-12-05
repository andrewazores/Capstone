package darren.esgl;

public final class Event {
    public static enum EventType {SEND, RECEIVE, INTERNAL}

    private final int eid;
    private final int pid;
    private final EventType type;
    private final Valuation val;
    private final VectorClock VC;

    public Event(int eid, int pid, EventType type, Valuation val, VectorClock VC) {
        this.eid = eid;
        this.pid = pid;
        this.type = type;
        this.val = val;
        this.VC = VC;
    }

    public int eid() {
        return eid;
    }

    public int pid() {
        return pid;
    }

    public EventType getType() {
        return type;
    }

    public Valuation getVal() {
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
}
