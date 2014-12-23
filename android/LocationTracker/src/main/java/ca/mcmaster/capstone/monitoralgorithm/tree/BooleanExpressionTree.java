package ca.mcmaster.capstone.monitoralgorithm.tree;

import java.util.Objects;

import ca.mcmaster.capstone.monitoralgorithm.Conjunct;
import ca.mcmaster.capstone.monitoralgorithm.ProcessState;
import ca.mcmaster.capstone.monitoralgorithm.tree.ComparisonOperator;
import ca.mcmaster.capstone.monitoralgorithm.tree.RootNode;
import ca.mcmaster.capstone.monitoralgorithm.tree.ValueNode;
import ca.mcmaster.capstone.monitoralgorithm.tree.VariableNode;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

@EqualsAndHashCode @ToString
public class BooleanExpressionTree {

    @NonNull private final RootNode root;

    //FIXME: This is garbage.
    /*
     * Very limited parsing of expression string of the form 'varibale' [==.!=] 'value'.
     */
    public BooleanExpressionTree(@NonNull final String expression) {
        final String[] tokens = expression.split(" ");
        ComparisonOperator op = ComparisonOperator.EQUAL;
        if (Objects.equals(tokens[1], "==")) {
            op = ComparisonOperator.EQUAL;
        } else if (Objects.equals(tokens[1], "!=")) {
            op = ComparisonOperator.NOT_EQUAL;
        }
        root = new RootNode(new VariableNode(tokens[0]), new ValueNode(Double.parseDouble(tokens[2])), op);
    }

    public Conjunct.Evaluation evaluate(@NonNull final ProcessState state) {
        final boolean evaluation = root.evaluate(state);
        if (evaluation) {
            return Conjunct.Evaluation.TRUE;
        } else {
            return Conjunct.Evaluation.FALSE;
        }
    }
}
