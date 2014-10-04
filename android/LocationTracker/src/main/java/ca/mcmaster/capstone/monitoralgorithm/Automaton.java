package ca.mcmaster.capstone.monitoralgorithm;

import java.util.List;

/* Class to represent an Automaton.*/
public class Automaton {
    private AutomatonState currentState;
    private List<AutomatonState> stateList;
    private List<AutomatonTransition> transitionList;

    public AutomatonState getCurrentState() {
        return currentState;
    }
}
