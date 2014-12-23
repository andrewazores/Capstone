package ca.mcmaster.capstone.monitoralgorithm;

import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

@EqualsAndHashCode @ToString
public class BooleanExpressionTree {
    public interface Node {
        Double evaluate(ProcessState state);
    }
    private interface Operator<T, R> {
        R apply(T ... args);
    }
    private interface BinaryOperator {}

    @AllArgsConstructor
    public static enum ArithmeticOperator implements Operator<Double, Double>, BinaryOperator {
        ADD(x -> x[0] + x[1]),
        SUBTRACT(x -> x[0] - x[1]),
        DIVIDE(x -> x[0] / x[1]),
        MULTIPLY(x -> x[0] * x[1]),
        EXPONENT(x -> Math.pow(x[0], x[1]));

        @NonNull private final Operator<Double, Double> operator;

        @Override
        public Double apply(final Double ... args) {
            if (args.length != 2) {
                throw new ArityException(2, args);
            }
            return this.operator.apply(args);
        }
    }

    @AllArgsConstructor
    public static enum ComparisonOperator implements Operator<Double, Boolean>, BinaryOperator {
        LESS_THAN(x -> x[0] < x[1]),
        GREATER_THAN(x -> x[0] > x[1]),
        EQUAL(x -> x[0].equals(x[1])),
        NOT_EQUAL(x -> !EQUAL.apply(x)),
        LESS_OR_EQUAL(x -> LESS_THAN.apply(x) || EQUAL.apply(x)),
        GREATER_OR_EQUAL(x -> GREATER_THAN.apply(x) || EQUAL.apply(x));

        @NonNull private final Operator<Double, Boolean> operator;

        @Override
        public Boolean apply(@NonNull final Double ... args) {
            if (args.length != 2) {
                throw new ArityException(2, args);
            }
            return this.operator.apply(args);
        }
    }

    /* Root node must be a comparison */
    @AllArgsConstructor @ToString
    private static class RootNode {
        @NonNull Node left;
        @NonNull Node right;
        @NonNull ComparisonOperator op;

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

    /* An inner node */
    @AllArgsConstructor @ToString
    private static class InnerNode implements Node {
        @NonNull Node left;
        @NonNull Node right;
        @NonNull ArithmeticOperator op;

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

    /* A leaf node */
    @AllArgsConstructor @ToString
    private static class VariableNode implements Node {
        @NonNull String variableName;

        @Override public Double evaluate(@NonNull final ProcessState state) {
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

    /* A leaf node */
    @AllArgsConstructor @ToString
    private static class ValueNode implements Node {
        @NonNull Double value;

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
