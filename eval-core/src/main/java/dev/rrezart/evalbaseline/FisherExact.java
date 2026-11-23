package dev.rrezart.evalbaseline;

/**
 * One-sided Fisher exact test: is the new pass rate lower than the baseline pass rate?
 * Exact for small run counts such as 5 or 10, where a z-test is unreliable.
 */
final class FisherExact {

    private FisherExact() {
    }

    /**
     * Probability of seeing {@code newPasses} or fewer passes in the new run if both runs
     * had the same true pass rate.
     */
    static double pLower(int basePasses, int baseRuns, int newPasses, int newRuns) {
        int totalPasses = basePasses + newPasses;
        int total = baseRuns + newRuns;
        int minNew = Math.max(0, totalPasses - baseRuns);
        double p = 0;
        for (int k = minNew; k <= newPasses; k++) {
            p += Math.exp(logChoose(newRuns, k) + logChoose(baseRuns, totalPasses - k) - logChoose(total, totalPasses));
        }
        return Math.min(1, p);
    }

    private static double logChoose(int n, int k) {
        if (k < 0 || k > n) {
            return Double.NEGATIVE_INFINITY;
        }
        return logFactorial(n) - logFactorial(k) - logFactorial(n - k);
    }

    private static double logFactorial(int n) {
        double s = 0;
        for (int i = 2; i <= n; i++) {
            s += Math.log(i);
        }
        return s;
    }
}
