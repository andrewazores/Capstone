package ca.mcmaster.capstone.monitoralgorithm;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

/* Class to represent an automaton state.*/
@Value @AllArgsConstructor
public class AutomatonState {
    @NonNull String stateName;
    @NonNull Automaton.Evaluation stateType;

    public AutomatonState(@NonNull final AutomatonState state) {
        this.stateName = state.stateName;
        this.stateType = state.stateType;
    }
}