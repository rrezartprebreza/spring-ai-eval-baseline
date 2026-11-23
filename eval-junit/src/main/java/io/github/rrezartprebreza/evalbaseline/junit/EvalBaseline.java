package io.github.rrezartprebreza.evalbaseline.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets the baseline file and thresholds for the {@link EvalTest} methods of a class.
 * Without it, the baseline is {@code <eval.baseline-dir>/<ClassName>.tsv}
 * ({@code src/test/resources/eval-baselines} by default) and the default thresholds apply.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface EvalBaseline {

    /** Baseline file, relative to the module directory; empty for the default location. */
    String value() default "";

    /** Significance level for a pass-rate drop. */
    double alpha() default 0.05;

    /** Smallest pass-rate drop that can fail a test, between 0 and 1. */
    double minDrop() default 0.10;

    /** Fail when p95 latency is above baseline times this factor; 0 turns it off. */
    double maxLatencyFactor() default 1.5;

    /** Fail when cost per run is above this; 0 turns it off. */
    double maxCostPerRun() default 0;
}
