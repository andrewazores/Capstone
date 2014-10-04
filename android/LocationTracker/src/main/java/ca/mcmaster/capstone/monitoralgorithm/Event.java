package ca.mcmaster.capstone.monitoralgorithm;

public class Event {
    private int eid;
    private int pid;

    public Event(int eid, int pid) {
        this.eid = eid;
        this.pid = pid;
    }

    public int getEid() {
        return eid;
    }

    public int getPid() {
        return pid;
    }
}
