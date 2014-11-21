package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashMap;
import java.util.Map;

// XXX: The Value<T> class basically breaks type safety. We need to find a better way to do this.
public class Valuation {
    public static class Value<T> {
        private final T value;

        public Value(final T value) {
            this.value = value;
        }

        public T evaluate() {
            return value;
        }
    }

    private final Map<String, Value<?>> valuation = new HashMap<>();

    public Valuation(String variableName, Value<?> value) {
        this.valuation.put(variableName, value);
    }

    public Valuation(Valuation valuation) {
        this.valuation.putAll(valuation.valuation);
    }

    public Value<?> add(String variableName, Value<?> value) {
        return this.valuation.put(variableName, value);
    }
}
