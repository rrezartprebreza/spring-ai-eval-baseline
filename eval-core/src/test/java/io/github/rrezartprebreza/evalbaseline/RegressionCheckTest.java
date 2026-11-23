package io.github.rrezartprebreza.evalbaseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.rrezartprebreza.evalbaseline.RegressionCheck.Options;
import io.github.rrezartprebreza.evalbaseline.RegressionCheck.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegressionCheckTest {

    private static CaseResult r(String id, int runs, int passes, long p95) {
        return new CaseResult(id, runs, passes, p95, 0);
    }

    @Test
    void fisherExactMatchesHandComputedValues() {
        assertEquals(252.0 / 15504, FisherExact.pLower(10, 10, 5, 10), 1e-9);
        assertEquals(0.5, FisherExact.pLower(9, 10, 8, 10), 1e-9);
    }

    @Test
    void smallDropIsNotARegression() {
        Baseline base = Baseline.of(List.of(r("a", 10, 9, 100)));
        var report = RegressionCheck.compare(base, List.of(r("a", 10, 8, 100)), Options.defaults());
        assertFalse(report.failed());
    }

    @Test
    void largeSignificantDropIsARegression() {
        Baseline base = Baseline.of(List.of(r("a", 10, 10, 100)));
        var report = RegressionCheck.compare(base, List.of(r("a", 10, 5, 100)), Options.defaults());
        assertTrue(report.failed());
        assertEquals(Status.PASS_RATE_DROP, report.findings().get(0).status());
    }

    @Test
    void largeButNotSignificantDropStillChecksLatency() {
        Baseline base = Baseline.of(List.of(r("a", 3, 3, 100)));
        var report = RegressionCheck.compare(base, List.of(r("a", 3, 2, 500)), Options.defaults());
        assertEquals(Status.SLOWER, report.findings().get(0).status());
    }

    @Test
    void caseMissingFromBaselineIsNew() {
        var report = RegressionCheck.compare(Baseline.empty(), List.of(r("a", 5, 1, 100)), Options.defaults());
        assertEquals(Status.NEW, report.findings().get(0).status());
        assertFalse(report.failed());
    }

    @Test
    void costOverBudgetFails() {
        var result = new CaseResult("a", 10, 10, 100, 0.5);
        var report = RegressionCheck.compare(Baseline.empty(), List.of(result), Options.defaults().withMaxCostPerRun(0.01));
        assertEquals(Status.OVER_BUDGET, report.findings().get(0).status());
    }

    @Test
    void assertNoRegressionThrowsWithMarkdown() {
        Baseline base = Baseline.of(List.of(r("a", 10, 10, 100)));
        AssertionError e = assertThrows(AssertionError.class,
                () -> RegressionCheck.assertNoRegression(base, List.of(r("a", 10, 4, 100)), Options.defaults()));
        assertTrue(e.getMessage().contains("PASS_RATE_DROP"));
    }

    @Test
    void baselineRoundTrip(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub/baseline.tsv");
        Baseline.of(List.of(new CaseResult("refund", 10, 9, 1840, 0.0042))).write(file);
        Baseline read = Baseline.read(file);
        assertEquals(new CaseResult("refund", 10, 9, 1840, 0.0042), read.get("refund").orElseThrow());
        assertTrue(Files.readString(file).startsWith(Baseline.HEADER));
    }

    @Test
    void missingBaselineFileIsEmpty(@TempDir Path dir) throws Exception {
        assertTrue(Baseline.read(dir.resolve("none.tsv")).cases().isEmpty());
    }

    @Test
    void runnerCountsExceptionsAsFailures() {
        int[] n = {0};
        CaseResult result = RepeatedRunner.run("a", 4, () -> {
            n[0]++;
            if (n[0] == 2) {
                throw new IllegalStateException("timeout");
            }
            return RepeatedRunner.Trial.of(n[0] != 3, 0.01);
        });
        assertEquals(4, result.runs());
        assertEquals(2, result.passes());
        assertEquals(0.03, result.totalCost(), 1e-9);
    }
}
