package ca.mcmaster.capstone.monitoralgorithm.tree;

import ca.mcmaster.capstone.monitoralgorithm.ProcessState;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Node;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

/* A leaf node */
@AllArgsConstructor @ToString
public class VariableNode implements Node {
    @NonNull String variableName;

    @Override
    public Double evaluate(@NonNull final ProcessState state) {
        //TODO: refactor Valuation to use Double
        return (Double) state.getVal().getValue(variableName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VariableNode that = (VariableNode) o;

        if (!variableName.equals(that.variableName)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return variableName.hashCode();
    }
}
