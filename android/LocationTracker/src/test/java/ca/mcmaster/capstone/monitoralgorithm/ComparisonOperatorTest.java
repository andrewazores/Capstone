package ca.mcmaster.capstone.monitoralgorithm;

import android.provider.Telephony;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static ca.mcmaster.capstone.monitoralgorithm.BooleanExpressionTree.ComparisonOperator.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class ComparisonOperatorTest {

    private static void test(final double first, final BooleanExpressionTree.ComparisonOperator operator,
                             final double second, final boolean expected) {
        final boolean result = operator.apply(first, second);
        assertThat(String.format("%s is broke for (%f, %f)!", operator, first, second), result, is(expected));
    }

    @Test(expected = ArityException.class) public void testArity1() {
        LESS_THAN.apply(1.0, 2.0, 3.0);
    }

    @Test(expected = ArityException.class) public void testArity2() {
        LESS_THAN.apply(1.0);
    }

    @Test public void testLessThan1() {
        test(1, LESS_THAN, 2, true);
    }

    @Test public void testLessThan2() {
        test(2, LESS_THAN, 1, false);
    }

    @Test public void testLessThan3() {
        test(-1, LESS_THAN, 0, true);
    }

    @Test public void testLessThan4() {
        test(Double.NEGATIVE_INFINITY, LESS_THAN, Double.POSITIVE_INFINITY, true);
    }

    @Test public void testLessThan5() {
        test(5, LESS_THAN, 5, false);
    }

    @Test public void testGreaterThan1() {
        test(1, GREATER_THAN, 2, false);
    }

    @Test public void testGreaterThan2() {
        test(2, GREATER_THAN, 1, true);
    }

    @Test public void testGreaterThan3() {
        test(-1, GREATER_THAN, 0, false);
    }

    @Test public void testGreaterThan4() {
        test(Double.POSITIVE_INFINITY - 1, GREATER_THAN, Double.POSITIVE_INFINITY, false);
    }

    @Test public void testGreaterThan5() {
        test(5, GREATER_THAN, 5, false);
    }

    @Test public void testEqual1() {
        test(1, EQUAL, 1, true);
    }

    @Test public void testEqual2() {
        test(0, EQUAL, 1, false);
    }

    @Test public void testEqual3() {
        test(Double.POSITIVE_INFINITY, EQUAL, 0, false);
    }

    @Test public void testEqual4() {
        test(Double.POSITIVE_INFINITY, EQUAL, Double.POSITIVE_INFINITY, true);
    }

    @Test public void testLessOrEqual1() {
        test(1, LESS_OR_EQUAL, 2, true);
    }

    @Test public void testLessOrEqual2() {
        test(2, LESS_OR_EQUAL, 1, false);
    }

    @Test public void testLessOrEqual3() {
        test(-1, LESS_OR_EQUAL, 0, true);
    }

    @Test public void testLessOrEqual4() {
        test(Double.NEGATIVE_INFINITY, LESS_OR_EQUAL, Double.POSITIVE_INFINITY, true);
    }

    @Test public void testLessOrEqual5() {
        test(5, LESS_OR_EQUAL, 5, true);
    }

    @Test public void testGreaterOrEqual1() {
        test(1, GREATER_OR_EQUAL, 2, false);
    }

    @Test public void testGreaterOrEqual2() {
        test(2, GREATER_OR_EQUAL, 1, true);
    }

    @Test public void testGreaterOrEqual3() {
        test(-1, GREATER_OR_EQUAL, 0, false);
    }

    @Test public void testGreaterOrEqual4() {
        test(Double.NEGATIVE_INFINITY, GREATER_OR_EQUAL, Double.POSITIVE_INFINITY, false);
    }

    @Test public void testGreaterOrEqual5() {
        test(5, GREATER_OR_EQUAL, 5, true);
    }
}
