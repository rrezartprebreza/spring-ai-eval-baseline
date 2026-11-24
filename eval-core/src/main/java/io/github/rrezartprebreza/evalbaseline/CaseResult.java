package io.github.rrezartprebreza.evalbaseline;

import java.util.Arrays;

/**
 * Outcome of running one eval case several times.
 *
 * @param caseId     stable id of the case, used to match it with the baseline
 * @param runs       how many times the case ran
 * @param passes     how many of those runs passed
 * @param p95Millis  95th percentile latency in milliseconds, or -1 if not measured
 * @param totalCost  summed cost of all runs in your own unit (for example USD), 0 if not measured
 * @param scores     scores between 0 and 1 reported by the runs, {@link ScoreStats#NONE} if none
 */
public record CaseResult(String caseId, int runs, int passes, long p95Millis, double totalCost, ScoreStats scores) {

    /** A result without scores. */
    public CaseResult(String caseId, int runs, int passes, long p95Millis, double totalCost) {
        this(caseId, runs, passes, p95Millis, totalCost, ScoreStats.NONE);
    }

    public CaseResult {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId is required");
        }
        if (caseId.contains("\t") || caseId.contains("\n")) {
            throw new IllegalArgumentException("caseId must not contain tabs or line breaks");
        }
        if (runs <= 0 || passes < 0 || passes > runs) {
            throw new IllegalArgumentException("need runs > 0 and 0 <= passes <= runs");
        }
        if (scores == null) {
            scores = ScoreStats.NONE;
        }
    }

    public double passRate() {
        return (double) passes / runs;
    }

    public Interval interval() {
        return Interval.wilson95(passes, runs);
    }

    public double costPerRun() {
        return totalCost / runs;
    }

    /** 95th percentile (nearest rank) of the given latencies, or -1 for none. */
    public static long p95(long[] millis) {
        if (millis.length == 0) {
            return -1;
        }
        long[] sorted = millis.clone();
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(0.95 * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }
}
