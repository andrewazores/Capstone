package ca.mcmaster.capstone.monitoralgorithm.tree;

import ca.mcmaster.capstone.monitoralgorithm.ProcessState;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Node;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

/* An inner node */
@AllArgsConstructor
@ToString
public class InnerNode implements Node {
    @NonNull Node left;
    @NonNull Node right;
    @NonNull
    ArithmeticOperator op;

    @Override public Double evaluate(@NonNull final ProcessState state) {
        return op.apply(left.evaluate(state), right.evaluate(state));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InnerNode innerNode = (InnerNode) o;

        if (!left.equals(innerNode.left)) return false;
        if (op != innerNode.op) return false;
        if (!right.equals(innerNode.right)) return false;

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
