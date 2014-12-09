package ca.mcmaster.capstone.monitoralgorithm;

public class BooleanExpressionTree {
    private interface Node {
        Double evaluate(ProcessState state);
    }
    private interface Operator<T, R> {
        R apply(T ... args);
    }
    private interface BinaryOperator {}

    public static enum ArithmeticOperator implements Operator<Double, Double>, BinaryOperator {
        ADD(x -> x[0] + x[1]),
        SUBTRACT(x -> x[0] - x[1]),
        DIVIDE(x -> x[0] / x[1]),
        MULTIPLY(x -> x[0] * x[1]),
        EXPONENT(x -> Math.pow(x[0], x[1]));

        private final Operator<Double, Double> operator;

        private ArithmeticOperator(final Operator<Double, Double> operator) {
            this.operator = operator;
        }

        @Override
        public Double apply(final Double ... args) {
            if (args.length != 2) {
                throw new ArityException(2, args);
            }
            return this.operator.apply(args);
        }
    }

    public static enum ComparisonOperator implements Operator<Double, Boolean>, BinaryOperator {
        LESS_THAN(x -> x[0] < x[1]),
        GREATER_THAN(x -> x[0] > x[1]),
        EQUAL(x -> x[0].equals(x[1])),
        LESS_OR_EQUAL(x -> LESS_THAN.apply(x) || EQUAL.apply(x)),
        GREATER_OR_EQUAL(x -> GREATER_THAN.apply(x) || EQUAL.apply(x));

        private final Operator<Double, Boolean> operator;

        private ComparisonOperator(final Operator<Double, Boolean> operator) {
            this.operator = operator;
        }

        @Override
        public Boolean apply(final Double ... args) {
            if (args.length != 2) {
                throw new ArityException(2, args);
            }
            return this.operator.apply(args);
        }
    }

    /* Root node must be a comparison */
    private static class RootNode {
        private final Node left;
        private final Node right;
        private final ComparisonOperator op;

        public RootNode(final Node left, final Node right, final ComparisonOperator op) {
            this.left = left;
            this.right = right;
            this.op = op;
        }

        public Boolean evaluate(ProcessState state) {
            return op.apply(left.evaluate(state), right.evaluate(state));
        }
    }

    /* An inner node */
    private static class InnerNode implements Node {
        private final Node left;
        private final Node right;
        private final ArithmeticOperator op;

        public InnerNode(final Node left, final Node right, final ArithmeticOperator op) {
            this.left = left;
            this.right = right;
            this.op = op;
        }

        public Double evaluate(ProcessState state) {
            return op.apply(left.evaluate(state), right.evaluate(state));
        }
    }

    /* A leaf node */
    private static class LeafNode implements Node {
        private final String variableName;

        public LeafNode(final String variableName) {
            this.variableName = variableName;
        }

        public Double evaluate(ProcessState state) {
            //TODO: refactor Valuation to use Double
            return (Double) state.getVal().getValue(variableName);
        }
    }

    private final RootNode root = new RootNode(null, null, ComparisonOperator.EQUAL);

    public Conjunct.Evaluation evaluate(ProcessState state) {
        boolean evaluation = root.evaluate(state);
        if (evaluation) {
            return Conjunct.Evaluation.TRUE;
        } else {
            return Conjunct.Evaluation.FALSE;
        }
    }
}