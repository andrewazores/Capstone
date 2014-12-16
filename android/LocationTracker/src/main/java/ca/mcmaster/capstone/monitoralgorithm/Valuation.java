package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

@EqualsAndHashCode @ToString
public class Valuation<T> {
    private final Map<String, T> valuation = new HashMap<>();

    public Valuation(@NonNull final String variableName, @NonNull final T value) {
        this.valuation.put(variableName, value);
    }

    public Valuation(@NonNull final Valuation<T> valuation) {
        this.valuation.putAll(valuation.valuation);
    }

    public T add(@NonNull final String variableName, @NonNull final T value) {
        return this.valuation.put(variableName, value);
    }

    public T getValue(@NonNull final String name){
        return valuation.get(name);
    }
}
