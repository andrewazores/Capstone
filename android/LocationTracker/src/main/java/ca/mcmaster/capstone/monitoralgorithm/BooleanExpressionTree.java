package ca.mcmaster.capstone.monitoralgorithm;

import java.util.Arrays;

public class BooleanExpressionTree {
    private interface Node {}
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
                throw new IllegalArgumentException("Invalid number of arguments for " + this + ": " + Arrays.toString(args));
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
                throw new IllegalArgumentException("Invalid number of arguments for " + this + ": " + Arrays.toString(args));
            }
            return this.operator.apply(args);
        }
    }

    /* An inner node */
    private static class InnerNode implements Node {
        private final Node left;
        private final Node right;
        private final Operator<?, ?> op;

        public InnerNode(final Node left, final Node right, final Operator<?, ?> op) {
            this.left = left;
            this.right = right;
            this.op = op;
        }
    }

    /* A leaf node */
    private static class LeafNode implements Node {
        private final double value;

        public LeafNode(final double value) {
            this.value = value;
        }
    }

    private final Node root = new LeafNode(0.0);
}
