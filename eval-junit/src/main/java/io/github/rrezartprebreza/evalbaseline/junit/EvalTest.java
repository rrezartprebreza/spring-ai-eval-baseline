package io.github.rrezartprebreza.evalbaseline.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test that is run several times and judged on its pass rate against a baseline.
 *
 * <p>Each run passes when the method returns normally, and fails when it throws an assertion
 * error or calls {@link EvalTrial#fail(String)}. Write the method like any JUnit test:
 *
 * <pre>{@code
 * @EvalTest(runs = 10)
 * void refundAnswerMentionsTheLimit() {
 *     assertThat(bot.answer("Can I get a refund?")).contains("30 days");
 * }
 * }</pre>
 *
 * <p>After the runs, the pass rate is compared with the class baseline (see {@link EvalBaseline}).
 * The test fails only on a significant drop, a large latency increase or a cost overrun.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Test
@ExtendWith(EvalExtension.class)
public @interface EvalTest {

    /** How many times to run the method. */
    int runs() default 10;

    /** Id of the case in the baseline file; the method name when empty. */
    String caseId() default "";
}
