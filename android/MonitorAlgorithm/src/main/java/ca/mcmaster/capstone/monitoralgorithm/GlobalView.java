import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;

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

    public void setStates(List<ProcessState> states) {
        this.states = states;
    }

    public VectorClock getCut() {
        return cut;
    }

    public void setCut(VectorClock cut) {
        this.cut = cut;
    }

    public AutomatonState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(AutomatonState state) {
        this.currentState = state;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public void setTokens(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Queue<Event> getPendingEvents() {
        return pendingEvents;
    }

    public List<AutomatonTransition> getPendingTransitions() {
        return pendingTransitions;
    }

    /*
     * Updates the global view with token.
     *
     * @param token The token to use to update the global view.
     */
    public void update(Token token) {
        cut = cut.merge(token.getCut());
        states.set(token.getTargetProcessState().getId(), token.getTargetProcessState());
        // If any pending transitions are also in the token, and are evaluated in the token, remove them
        for (Iterator<AutomatonTransition> it = pendingTransitions.iterator(); it.hasNext();) {
            AutomatonTransition pending = it.next();
            if (token.getAutomatonTransitions().contains(pending) && pending.getEvaluation() != AutomatonTransition.Evaluation.NONE) {
                it.remove();
            }
        }
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