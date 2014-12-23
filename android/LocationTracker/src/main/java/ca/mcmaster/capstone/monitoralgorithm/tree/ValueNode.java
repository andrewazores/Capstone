package ca.mcmaster.capstone.monitoralgorithm.tree;

import ca.mcmaster.capstone.monitoralgorithm.ProcessState;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Node;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

/* A leaf node */
@AllArgsConstructor @ToString
public class ValueNode implements Node {
    @NonNull Double value;

    @Override
    public Double evaluate(@NonNull final ProcessState state) {
        //TODO: refactor Valuation to use Double
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ValueNode valueNode = (ValueNode) o;

        if (!value.equals(valueNode.value)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
