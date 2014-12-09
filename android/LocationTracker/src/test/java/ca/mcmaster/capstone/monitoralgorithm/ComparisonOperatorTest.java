package ca.mcmaster.capstone.monitoralgorithm;

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

    private static void test(final BooleanExpressionTree.ComparisonOperator operator,
                             final double first, final double second, final boolean expected) {
        final boolean result = operator.apply(first, second);
        assertThat(String.format("%s is broke for (%f, %f)!", operator, first, second), result, is(expected));
    }

    @Test public void testLessThan1() {
        test(LESS_THAN, 1, 2, true);
    }

    @Test public void testLessThan2() {
        test(LESS_THAN, 2, 1, false);
    }

    @Test public void testLessThan3() {
        test(LESS_THAN, -1, 0, true);
    }

    @Test public void testLessThan4() {
        test(LESS_THAN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true);
    }

    @Test public void testLessThan5() {
        test(LESS_THAN, 5, 5, false);
    }

    @Test public void testGreaterThan1() {
        test(GREATER_THAN, 1, 2, false);
    }

    @Test public void testGreaterThan2() {
        test(GREATER_THAN, 2, 1, true);
    }

    @Test public void testGreaterThan3() {
        test(GREATER_THAN, -1, 0, false);
    }

    @Test public void testGreaterThan4() {
        test(GREATER_THAN, Double.POSITIVE_INFINITY - 1, Double.POSITIVE_INFINITY, false);
    }

    @Test public void testGreaterThan5() {
        test(GREATER_THAN, 5, 5, false);
    }

    @Test public void testEqual1() {
        test(EQUAL, 1, 1, true);
    }

    @Test public void testEqual2() {
        test(EQUAL, 0, 1, false);
    }

    @Test public void testEqual3() {
        test(EQUAL, Double.POSITIVE_INFINITY, 0, false);
    }

    @Test public void testEqual4() {
        test(EQUAL, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true);
    }

    @Test public void testLessOrEqual1() {
        test(LESS_OR_EQUAL, 1, 2, true);
    }

    @Test public void testLessOrEqual2() {
        test(LESS_OR_EQUAL, 2, 1, false);
    }

    @Test public void testLessOrEqual3() {
        test(LESS_OR_EQUAL, -1, 0, true);
    }

    @Test public void testLessOrEqual4() {
        test(LESS_OR_EQUAL, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true);
    }

    @Test public void testLessOrEqual5() {
        test(LESS_OR_EQUAL, 5, 5, true);
    }

    @Test public void testGreaterOrEqual1() {
        test(GREATER_OR_EQUAL, 1, 2, false);
    }

    @Test public void testGreaterOrEqual2() {
        test(GREATER_OR_EQUAL, 2, 1, true);
    }

    @Test public void testGreaterOrEqual3() {
        test(GREATER_OR_EQUAL, -1, 0, false);
    }

    @Test public void testGreaterOrEqual4() {
        test(GREATER_OR_EQUAL, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false);
    }

    @Test public void testGreaterOrEqual5() {
        test(GREATER_OR_EQUAL, 5, 5, true);
    }
}
