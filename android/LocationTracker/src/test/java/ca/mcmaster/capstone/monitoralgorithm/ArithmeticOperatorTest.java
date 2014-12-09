package ca.mcmaster.capstone.monitoralgorithm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static ca.mcmaster.capstone.monitoralgorithm.BooleanExpressionTree.ArithmeticOperator.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class ArithmeticOperatorTest {

    private static void test(final BooleanExpressionTree.ArithmeticOperator operator,
                             final double first, final double second, final double expected) {
        final double result = operator.apply(first, second);
        assertThat(operator + " is broke!", result, is(expected));
    }

    @Test public void testAdd1() {
        test(ADD, 1, 1, 2);
    }

    @Test public void testAdd2() {
        test(ADD, -1, -1, -2);
    }

    @Test public void testSubtract1() {
        test(SUBTRACT, 1, 1, 0);
    }

    @Test public void testSubtract2() {
        test(SUBTRACT, -1, -1, 0);
    }

    @Test public void testDivide1() {
        test(DIVIDE, 1, 2, 0.5);
    }

    @Test public void testDivide2() {
        test(DIVIDE, 4, 2, 2);
    }

    @Test public void testDivide3() {
        test(DIVIDE, 1, 0, Double.POSITIVE_INFINITY);
    }

    @Test public void testDivide4() {
        test(DIVIDE, 1, Double.POSITIVE_INFINITY, 0);
    }

    @Test public void testMultiply1() {
        test(MULTIPLY, 1, 1, 1);
    }

    @Test public void testMultiply2() {
        test(MULTIPLY, 1, -1, -1);
    }

    @Test public void testMultiply3() {
        test(MULTIPLY, 1, 0, 0);
    }

    @Test public void testMultiply4() {
        test(MULTIPLY, 1, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

}
