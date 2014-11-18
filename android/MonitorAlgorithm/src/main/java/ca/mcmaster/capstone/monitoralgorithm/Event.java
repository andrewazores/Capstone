public final class Event {
    private static enum EventType {SEND, RECEIVE, INTERNAL}

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
}
