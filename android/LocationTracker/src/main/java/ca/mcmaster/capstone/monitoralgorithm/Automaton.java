package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/* Class to represent an Automaton.*/
public class Automaton {
    public static enum Evaluation {SATISFIED, VIOLATED, UNDECIDED}

    private final static AutomatonState initialState = new AutomatonState("Start", Evaluation.UNDECIDED);
    private final static Set<AutomatonState> stateList = new HashSet<>();
    private final static Set<AutomatonTransition> transitionList = new HashSet<>();

    /*
     * Gets the initial state of the automaton.
     *
     * @return The initial state of the automaton.
     */
    public static AutomatonState getInitialState() {
        return initialState;
    }

    /*
     * Computes the next state based on the given GlobalView.
     *
     * @param gv The GlobalView to use to compute the next state.
     * @return The next state of the automaton.
     */
    public static AutomatonState advance(final GlobalView gv) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public static Set<AutomatonTransition> getTransitions() {
        return transitionList;
    }
}
