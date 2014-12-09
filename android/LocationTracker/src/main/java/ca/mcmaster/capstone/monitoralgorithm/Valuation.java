package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashMap;
import java.util.Map;

public class Valuation<T> {
    private final Map<String, T> valuation = new HashMap<>();

    public Valuation(final String variableName, final T value) {
        this.valuation.put(variableName, value);
    }

    public Valuation(Valuation valuation) {
        this.valuation.putAll(valuation.valuation);
    }

    public T add(final String variableName, final T value) {
        return this.valuation.put(variableName, value);
    }

    public T getValue(final String name){
        return valuation.get(name);
    }
}
