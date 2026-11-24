package io.github.rrezartprebreza.evalbaseline.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs an {@link EvalTest} once per row of a dataset file instead of a fixed number of times.
 * Declare an {@link io.github.rrezartprebreza.evalbaseline.EvalCase} parameter to receive the row:
 *
 * <pre>{@code
 * @EvalTest
 * @EvalDataset("src/test/resources/refund-questions.jsonl")
 * void answersRefundQuestions(EvalCase c) {
 *     assertThat(bot.answer(c.get("question"))).contains(c.get("mustMention"));
 * }
 * }</pre>
 *
 * The pass rate over all rows is compared with the baseline, like any other eval test, and the
 * failure message lists the rows that failed. {@link EvalTest#runs()} is ignored.
 * See {@link io.github.rrezartprebreza.evalbaseline.Datasets} for the JSONL, JSON and CSV formats.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EvalDataset {

    /** Dataset file, relative to the module directory: {@code .jsonl}, {@code .json} or {@code .csv}. */
    String value();

    /** How many times to run each row, for models that answer differently each time. */
    int repeat() default 1;
}
