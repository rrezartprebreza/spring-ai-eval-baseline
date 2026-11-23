package io.github.rrezartprebreza.evalbaseline;

/** A confidence interval for a pass rate, both ends between 0 and 1. */
public record Interval(double low, double high) {

    /**
     * Wilson score interval for {@code passes} out of {@code runs}.
     * Works for small run counts and for 0 or all passes, where the normal approximation fails.
     *
     * @param z 1.96 for a 95% interval
     */
    public static Interval wilson(int passes, int runs, double z) {
        if (runs <= 0) {
            throw new IllegalArgumentException("runs must be positive");
        }
        if (passes < 0 || passes > runs) {
            throw new IllegalArgumentException("passes must be between 0 and runs");
        }
        double n = runs;
        double p = passes / n;
        double z2 = z * z;
        double centre = (p + z2 / (2 * n)) / (1 + z2 / n);
        double half = (z / (1 + z2 / n)) * Math.sqrt(p * (1 - p) / n + z2 / (4 * n * n));
        return new Interval(Math.max(0, centre - half), Math.min(1, centre + half));
    }

    public static Interval wilson95(int passes, int runs) {
        return wilson(passes, runs, 1.96);
    }
}
