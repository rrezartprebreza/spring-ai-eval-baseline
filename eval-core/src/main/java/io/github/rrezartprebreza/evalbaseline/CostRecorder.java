package io.github.rrezartprebreza.evalbaseline;

/**
 * Collects the cost of the current run on this thread, so code deep inside a test (for example
 * a cost-tracking chat model) can report cost without access to the test's trial object.
 *
 * <p>The JUnit extension starts a fresh total before each run and reads it after. Outside a run,
 * {@link #add(double)} does nothing.
 */
public final class CostRecorder {

    private static final ThreadLocal<double[]> CURRENT = new ThreadLocal<>();

    private CostRecorder() {
    }

    /** Adds to the cost of the run in progress on this thread. */
    public static void add(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("cost must not be negative");
        }
        double[] total = CURRENT.get();
        if (total != null) {
            total[0] += amount;
        }
    }

    /** Starts collecting for a run on this thread. */
    public static void start() {
        CURRENT.set(new double[1]);
    }

    /** Stops collecting and returns the total since {@link #start()}. */
    public static double stop() {
        double[] total = CURRENT.get();
        CURRENT.remove();
        return total == null ? 0 : total[0];
    }
}
