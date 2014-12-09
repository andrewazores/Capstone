package ca.mcmaster.capstone.monitoralgorithm;

public class ProcessState {
    private final int id;
    private final Valuation<?> val;
    private final VectorClock VC;

    /*
     * Construct a new ProcessState.
     *
     * @param id An identifier for this ProcessState.
     * @param val The valuation of the process' variables.
     * @param VC The vector clock for the process.
     */
    public ProcessState(final int id, final Valuation<?> val, final VectorClock VC) {
        this.id = id;
        this.val = val;
        this.VC = VC;
    }

    public ProcessState(final ProcessState state) {
        this.id = state.id;
        this.val = new Valuation<>(state.val);
        this.VC = new VectorClock(state.VC);
    }

    public int getId() {
        return id;
    }

    public Valuation<?> getVal() {
        return new Valuation<>(val);
    }

    public VectorClock getVC() {
        return new VectorClock(VC);
    }

    /*
         * Updates the ProcessState with the state after event occurs.
         *
         * @param event The event to update to.
         * @return A new ProcessState updated with event.
         */
    public ProcessState update(final Event event) {
        return new ProcessState(this.id, event.getVal(), event.getVC());
    }
}
