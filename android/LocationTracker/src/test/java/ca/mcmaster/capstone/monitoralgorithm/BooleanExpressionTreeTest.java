package ca.mcmaster.capstone.monitoralgorithm;

import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class BooleanExpressionTreeTest {
    @Before
    public void before() {

    }

    @After
    public void after() {

    }

    @Test
    public void testArithmeticOperatorAdd() {
        final double first = 1, second = 2, expected = 3,
                result = BooleanExpressionTree.ArithmeticOperator.ADD.apply(first, second);
        assertThat("1 + 2 should be 3", result, is(expected));
    }
}