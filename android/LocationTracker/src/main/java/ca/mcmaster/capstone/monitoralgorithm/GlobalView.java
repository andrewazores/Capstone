package ca.mcmaster.capstone.monitoralgorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/* Class to represent the local view of the global state.*/
public class GlobalView {
    private List<ProcessState> states;
    private VectorClock cut;
    private AutomatonState currentState;
    private List<Token> tokens;
    private final Queue<Event> pendingEvents;
    private final List<AutomatonTransition> pendingTransitions;

    public GlobalView() {
        this.states = new ArrayList<ProcessState>();
        this.cut = new VectorClock();
        this.currentState = new AutomatonState("", "");
        this.tokens = new ArrayList();
        this.pendingEvents = new ArrayDeque<Event>();
        this.pendingTransitions = new ArrayList<AutomatonTransition>();
    }

    public List<ProcessState> getStates() {
        return states;
    }

    public void setStates(List<ProcessState> states) { this.states = states; }

    public VectorClock getCut() {
        return cut;
    }

    public void setCut(VectorClock cut) {
        this.cut = cut;
    }

    public AutomatonState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(AutomatonState state) { this.currentState = state; }

    public List<Token> getTokens() {
        return tokens;
    }

    public void setTokens(List<Token> tokens) { this.tokens = tokens; }

    public Queue<Event> getPendingEvents() {
        return pendingEvents;
    }

    public List<AutomatonTransition> getPendingTransitions() {
        return pendingTransitions;
    }


    public void update(Token token) {

    }

    public void updateProcessState(int processID, Event event) {

    }

    /*
     * Checks if the GlobalView is consistent for all known processes.
     *
     * @return True if the GlobalView is consistent, False if not.
     */
    public boolean isConsistent() {
        boolean consistent = true;
        for (int i = 0; i < cut.size(); ++i) {
            consistent &= this.states.get(i).getId() == this.cut.process(i);
        }
        return consistent;
    }
}