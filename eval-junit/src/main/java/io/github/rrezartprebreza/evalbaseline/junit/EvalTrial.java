package io.github.rrezartprebreza.evalbaseline.junit;

/**
 * Optional parameter of an {@link EvalTest} method: the current run, its cost, and a way to
 * fail the run without throwing.
 */
public final class EvalTrial {

    private final int runIndex;
    private final int runs;
    private double cost;
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

    boolean failed() {
        return failure != null;
    }
}
