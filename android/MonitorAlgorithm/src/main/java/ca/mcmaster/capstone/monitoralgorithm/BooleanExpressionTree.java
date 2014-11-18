/* Class to represent a boolean expression as a binary tree.*/
public class BooleanExpressionTree {
    private Node root;

    public BooleanExpressionTree(String expression) {
        this.root = buildExpression(expression);
    }

    private Node buildExpression(String expression) {
        // Just a garbage return for stub implementation
        return new Node<String>("", null, null);
    }

    public Node getRoot() {
        return root;
    }

    /* Class to represent a node in a tree.*/
    private class Node<T> {
        public Node(T data, Node left, Node right) {
            this.data = data;
            this.left = left;
            this.right = right;
        }

        private T data;
        private Node left;
        private Node right;

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }

        public Node getLeft() {
            return left;
        }

        public void setLeft(Node left) {
            this.left = left;
        }

        public Node getRight() {
            return right;
        }

        public void setRight(Node right) {
            this.right = right;
        }
    }
}
