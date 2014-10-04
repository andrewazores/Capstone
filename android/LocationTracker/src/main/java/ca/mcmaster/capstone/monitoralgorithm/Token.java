package ca.mcmaster.capstone.monitoralgorithm;

/* Class to represent the computation slicing tokens.*/
public class Token {
    private final String owner;
    private final String destination;
    private final int targetEventId;
    private final VectorClock cut;
    private final AutomatonTransition automatonTransition;
    private final int targetProcessState;

    public Token() {
        // Place holder initializations fro stub implementation.
        this.owner = "";
        this.destination = "";
        this.targetEventId = 0;
        this.cut = new VectorClock();
        this.automatonTransition = new AutomatonTransition(new AutomatonState("", ""),
                new AutomatonState("", ""),
                new BooleanExpressionTree(""));
        this.targetProcessState = 0;
    }

    public String getOwner() {
        return owner;
    }

    public String getDestination() {
        return destination;
    }

    public int getTargetEventId() {
        return targetEventId;
    }

    public VectorClock getCut() {
        return cut;
    }

    public AutomatonTransition getAutomatonTransition() {
        return automatonTransition;
    }

    public int getTargetProcessState() {
        return targetProcessState;
    }
}
