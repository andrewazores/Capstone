package ca.mcmaster.capstone.monitoralgorithm.tree;

import ca.mcmaster.capstone.monitoralgorithm.ProcessState;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Node;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

/* Root node must be a comparison */
@AllArgsConstructor
@ToString
public class RootNode {
    @NonNull Node left;
    @NonNull Node right;
    @NonNull
    ComparisonOperator op;

    public Boolean evaluate(@NonNull final ProcessState state) {
        return op.apply(left.evaluate(state), right.evaluate(state));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RootNode rootNode = (RootNode) o;

        if (!left.equals(rootNode.left)) return false;
        if (op != rootNode.op) return false;
        if (!right.equals(rootNode.right)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = left.hashCode();
        result = 31 * result + right.hashCode();
        result = 31 * result + op.hashCode();
        return result;
    }
}
