package ca.mcmaster.capstone.monitoralgorithm;

import java.util.ArrayList;
import java.util.List;

/* Class to represent the local view of the global state.*/
public class GlobalView {
    private final List<AutomatonState> state;
    private final VectorClock cut;
    private final AutomatonState currentState;
    private final List<Token> tokens;
    private final List<Event> pendingEvents;
    private final List<AutomatonTransition> pendingAutomatonTransitions;

    public GlobalView() {
        this.state = new ArrayList<AutomatonState>();
        this.cut = new VectorClock();
        this.currentState = new AutomatonState("", "");
        this.tokens = new ArrayList();
        this.pendingEvents = new ArrayList();
        this.pendingAutomatonTransitions = new ArrayList<AutomatonTransition>();
    }

    public List<AutomatonState> getState() {
        return state;
    }

    public VectorClock getCut() {
        return cut;
    }

    public AutomatonState getCurrentState() {
        return currentState;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public List<Event> getPendingEvents() {
        return pendingEvents;
    }

    public List<AutomatonTransition> getPendingAutomatonTransitions() {
        return pendingAutomatonTransitions;
    }
}