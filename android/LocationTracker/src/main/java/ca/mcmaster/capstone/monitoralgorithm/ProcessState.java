package ca.mcmaster.capstone.monitoralgorithm;

public class ProcessState {
    private final int id;
    private final Valuation valuation;

    public ProcessState(int id, Valuation valuation) {
        this.id = id;
        this.valuation = valuation;
    }

    public int getId() {
        return id;
    }

    public Valuation getValuation() {
        return new Valuation(valuation);
    }
}
