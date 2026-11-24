package io.github.rrezartprebreza.evalbaseline.junit;

/**
 * Optional parameter of an {@link EvalTest} method: the current run, its cost and score, and a
 * way to fail the run without throwing.
 *
 * <p>Cost reported through {@link io.github.rrezartprebreza.evalbaseline.CostRecorder} (for
 * example by a cost-tracking chat model) is added to the run automatically.
 */
public final class EvalTrial {

    private final int runIndex;
    private final int runs;
    private double cost;
    private double score = Double.NaN;
    private String failure;

    EvalTrial(int runIndex, int runs) {
        this.runIndex = runIndex;
        this.runs = runs;
    }

    /** 0 for the first run. */
    public int runIndex() {
        return runIndex;
    }

    public int runs() {
        return runs;
    }

    /** Adds to the cost of this run, in your own unit (for example USD). */
    public void addCost(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("cost must not be negative");
        }
        cost += amount;
    }

    /**
     * Records a quality score between 0 and 1 for this run, for checks that are not simply pass
     * or fail (similarity, a judge model's rating). The mean score is compared with the baseline.
     * A scored run still passes unless it fails.
     */
    public void score(double value) {
        if (Double.isNaN(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("a score must be between 0 and 1, got " + value);
        }
        score = value;
    }

    /** Marks this run as failed and carries on. */
    public void fail(String reason) {
        failure = reason == null ? "failed" : reason;
    }

    /** Fails this run when {@code condition} is false. */
    public void check(boolean condition, String reason) {
        if (!condition) {
            fail(reason);
        }
    }

    double cost() {
        return cost;
    }

    double scoreValue() {
        return score;
    }

    String failure() {
        return failure;
    }

    boolean failed() {
        return failure != null;
    }
}
