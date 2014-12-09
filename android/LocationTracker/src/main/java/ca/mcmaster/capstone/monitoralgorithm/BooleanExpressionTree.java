package ca.mcmaster.capstone.monitoralgorithm;

public class BooleanExpressionTree {
    private interface Node {}
    private interface Operator<T, R> {
        R apply(T ... args);
    }
    private interface Binary{}

    private abstract class BinaryOperator <T, R> implements Node, Operator<T, R>, Binary {
        @Override
        abstract public R apply(T ... args);
    }

    public class Add extends BinaryOperator<Double, Double> {
        public Double apply(Double ... args) {
            return args[0] + args[1];
        }
    }

    public class Subtract extends BinaryOperator<Double, Double> {
        public Double apply(Double ... args) {
            return args[0] - args[1];
        }
    }

    public class Divide extends BinaryOperator<Double, Double> {
        public Double apply(Double ... args) {
            return args[0] / args[1];
        }
    }

    public class Multiply extends BinaryOperator<Double, Double> {
        public Double apply(Double ... args) {
            return args[0] * args[1];
        }
    }

    public class LessThan extends BinaryOperator<Double, Boolean> {
        public Boolean apply(Double ... args) {
            return args[0] < args[1];
        }
    }

    public class GreaterThan extends BinaryOperator<Double, Boolean> {
        public Boolean apply(Double ... args) {
            return args[0] > args[1];
        }
    }

    public class LessOrEqual extends BinaryOperator<Double, Boolean> {
        public Boolean apply(Double ... args) {
            return args[0] <= args[1];
        }
    }

    public class GreaterOrEqual extends BinaryOperator<Double, Boolean> {
        public Boolean apply(Double ... args) {
            return args[0] >= args[1];
        }
    }

    public class Equal extends BinaryOperator<Double, Boolean> {
        public Boolean apply(Double ... args) {
            return args[0] == args[1];
        }
    }

    /* An inner node */
    private class InnerNode implements Node {
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
    private class LeafNode implements Node {
        private final Double value;

        public LeafNode(Double value) {
            this.value = new Double(value);
        }
    }

    private final Node root = new LeafNode(0.0);
}
