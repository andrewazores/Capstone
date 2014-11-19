package ca.mcmaster.capstone.monitoralgorithm;

import java.util.List;

/* Class to represent an Automaton.*/
public class Automaton {
    public static enum Evaluation {SATISFIED, VIOLATED, UNDECIDED};

    private AutomatonState currentState;
    private List<AutomatonState> stateList;
    private List<AutomatonTransition> transitionList;

    public Automaton() {

    }

    public AutomatonState getCurrentState() {
        return currentState;
    }

    /*
     * Computes the next state based on the given GlobalView.
     *
     * @param gv The GlobalView to use to compute the next state.
     * @return The next state of the automaton.
     */
    public AutomatonState advance(GlobalView gv) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /*
     * Maps currentState to an Evaluation.
     *
     * @return An Automaton.Evaluation corresponding to the current state of the automaton.
     */
    public Evaluation getEvaluation() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /*
     * Maps state to an Evaluation.
     *
     * @param state The state to determine the evaluation for.
     *
     * @return An Automaton.Evaluation corresponding to the given state.
     */
    public static Evaluation getEvalForState(AutomatonState state) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
