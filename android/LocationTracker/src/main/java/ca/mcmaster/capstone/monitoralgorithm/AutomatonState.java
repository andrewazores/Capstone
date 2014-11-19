package ca.mcmaster.capstone.monitoralgorithm;

/* Class to represent an automaton state.*/
public class AutomatonState {
    private String stateName;
    private String stateType;

    public AutomatonState(String stateName, String stateType) {
        this.stateName = stateName;
        this.stateType = stateType;
    }

    public AutomatonState(AutomatonState state) {
        this.stateName = new String(state.stateName);
        this.stateType = new String(state.stateType);
    }

    public String getStateType() {
        return stateType;
    }

    public String getStateName() {
        return stateName;
    }
}