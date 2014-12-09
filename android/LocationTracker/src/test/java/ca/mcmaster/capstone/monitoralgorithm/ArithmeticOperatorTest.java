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

    private static void test(final double first, final BooleanExpressionTree.ArithmeticOperator operator,
                             final double second, final double expected) {
        final double result = operator.apply(first, second);
        assertThat(String.format("%s is broke for (%f, %f)!", operator, first, second), result, is(expected));
    }

    @Test(expected = ArityException.class) public void testArity1() {
        ADD.apply(1.0, 2.0, 3.0);
    }

    @Test(expected = ArityException.class) public void testArity2() {
        ADD.apply(1.0);
    }

    @Test public void testAdd1() {
        test(1, ADD, 1, 2);
    }

    @Test public void testAdd2() {
        test(-1, ADD, -1, -2);
    }

    @Test public void testSubtract1() {
        test(1, SUBTRACT, 1, 0);
    }

    @Test public void testSubtract2() {
        test(-1, SUBTRACT, -1, 0);
    }

    @Test public void testDivide1() {
        test(1, DIVIDE, 2, 0.5);
    }

    @Test public void testDivide2() {
        test(4, DIVIDE, 2, 2);
    }

    @Test public void testDivide3() {
        test(1, DIVIDE, 0, Double.POSITIVE_INFINITY);
    }

    @Test public void testDivide4() {
        test(1, DIVIDE, Double.POSITIVE_INFINITY, 0);
    }

    @Test public void testMultiply1() {
        test(1, MULTIPLY, 1, 1);
    }

    @Test public void testMultiply2() {
        test(1, MULTIPLY, -1, -1);
    }

    @Test public void testMultiply3() {
        test(1, MULTIPLY, 0, 0);
    }

    @Test public void testMultiply4() {
        test(1, MULTIPLY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    @Test public void testExponent1() {
        test(1, EXPONENT, 0, 1);
    }

    @Test public void testExponent2() {
        test(0, EXPONENT, 1, 0);
    }

    @Test public void testExponent3() {
        test(2, EXPONENT, -1, 0.5);
    }

    @Test public void testExponent4() {
        test(2, EXPONENT, 2, 4);
    }
}
