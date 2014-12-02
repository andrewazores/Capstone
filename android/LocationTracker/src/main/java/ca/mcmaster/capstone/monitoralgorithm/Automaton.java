package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashSet;
import java.util.Set;

/* Class to represent an Automaton.*/
public class Automaton {
    public static enum Evaluation {SATISFIED, VIOLATED, UNDECIDED}

    private final static AutomatonState initialState = new AutomatonState("Start", Evaluation.UNDECIDED);
    private final static Set<AutomatonState> states = new HashSet<>();
    private final static Set<AutomatonTransition> transitions = new HashSet<>();

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
        for (AutomatonTransition transition : transitions) {
            if (transition.getFrom() == gv.getCurrentState() && transition.getFrom() != transition.getTo()) {
                if (transition.evaluate(gv.getStates().values()) == Conjunct.Evaluation.TRUE) {
                    return transition.getTo();
                }
            }
        }
        return gv.getCurrentState();
    }

    public static Set<AutomatonTransition> getTransitions() {
        return transitions;
    }
}
