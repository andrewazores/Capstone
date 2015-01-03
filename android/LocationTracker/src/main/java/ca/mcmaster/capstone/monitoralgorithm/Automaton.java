package ca.mcmaster.capstone.monitoralgorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ca.mcmaster.capstone.initializer.AutomatonFile;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

/* Class to represent an Automaton.*/
@EqualsAndHashCode @ToString
// FIXME: re-implement using Singleton pattern rather than static everything
public class Automaton {
    public static enum Evaluation {SATISFIED, VIOLATED, UNDECIDED}

    private static AutomatonState initialState;
    private static Map<String, AutomatonState> states;
    private static Set<AutomatonTransition> transitions;

    public static void processAutomatonFile(final AutomatonFile automatonFile) {
        // TODO: implement reading state names and transitions out of file and setting Automaton state
    }

    //FIXME: This is garbage.
    public static void build(@NonNull final NetworkPeerIdentifier id1, @NonNull final NetworkPeerIdentifier id2) {
        states = new HashMap<String, AutomatonState>() {{
            put("2", new AutomatonState("2", Evaluation.UNDECIDED));
            put("3", new AutomatonState("3", Evaluation.UNDECIDED));
            put("1", new AutomatonState("1", Evaluation.SATISFIED));
            put("0", new AutomatonState("0", Evaluation.SATISFIED));
        }};
        initialState = states.get("2");
        transitions = new HashSet<AutomatonTransition>() {{
            add(new AutomatonTransition(states.get("2"), states.get("0"), new ArrayList<Conjunct>() {{
                add(new Conjunct(id2, "x2 == 1.0"));
            }}));
            add(new AutomatonTransition(states.get("2"), states.get("3"), new ArrayList<Conjunct>() {{
                add(new Conjunct(id1, "x1 == 0.0"));
                add(new Conjunct(id2, "x2 != 1.0"));
            }}));
            add(new AutomatonTransition(states.get("3"), states.get("1"), new ArrayList<Conjunct>() {{
                add(new Conjunct(id1, "x1 == 0.0"));
                add(new Conjunct(id2, "x2 == 1.0"));
            }}));
        }};
    }

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
    public static AutomatonState advance(@NonNull final GlobalView gv) {
        for (final AutomatonTransition transition : transitions) {
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
