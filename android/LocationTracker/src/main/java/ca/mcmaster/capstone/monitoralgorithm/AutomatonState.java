package ca.mcmaster.capstone.monitoralgorithm;

/* Class to represent an automaton state.*/
public class AutomatonState {
    private String stateName;
    private Automaton.Evaluation stateType;

    public AutomatonState(final String stateName, final Automaton.Evaluation stateType) {
        this.stateName = stateName;
        this.stateType = stateType;
    }

    public AutomatonState(final AutomatonState state) {
        this.stateName = state.stateName;
        this.stateType = state.stateType;
    }

    public Automaton.Evaluation getStateType() {
        return stateType;
    }

    public String getStateName() {
        return stateName;
    }
}