package io.github.rrezartprebreza.evalbaseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalGateTest {

    private static CaseResult r(String id, int runs, int passes) {
        return new CaseResult(id, runs, passes, 100, 0);
    }

    @Test
    void failsOnRegressionAndWritesReport(@TempDir Path dir) throws Exception {
        Path baseline = dir.resolve("baseline.tsv");
        Baseline.of(List.of(r("a", 10, 10))).write(baseline);
        System.setProperty("eval.report-dir", dir.resolve("reports").toString());
        try {
            AssertionError e = assertThrows(AssertionError.class,
                    () -> EvalGate.verify("bot", baseline, List.of(r("a", 10, 4)), RegressionCheck.Options.defaults()));
            assertTrue(e.getMessage().contains("eval.update-baseline"));
            assertTrue(Files.readString(dir.resolve("reports/bot.md")).contains("PASS_RATE_DROP"));
        } finally {
            System.clearProperty("eval.report-dir");
        }
    }

    @Test
    void updateFlagWritesBaselineInsteadOfFailing(@TempDir Path dir) throws Exception {
        Path baseline = dir.resolve("baseline.tsv");
        Baseline.of(List.of(r("a", 10, 10), r("keep", 5, 5))).write(baseline);
        System.setProperty("eval.report-dir", dir.resolve("reports").toString());
        System.setProperty("eval.update-baseline", "true");
        try {
            EvalGate.verify("bot", baseline, List.of(r("a", 10, 4)), RegressionCheck.Options.defaults());
            Baseline updated = Baseline.read(baseline);
            assertEquals(4, updated.get("a").orElseThrow().passes());
            assertEquals(5, updated.get("keep").orElseThrow().passes());
        } finally {
            System.clearProperty("eval.report-dir");
            System.clearProperty("eval.update-baseline");
        }
    }

    @Test
    void comparisonTableHasOneColumnPerModel() {
        String md = ModelComparison.toMarkdown(Map.of("small", List.of(r("a", 10, 5))));
        assertTrue(md.contains("| Case | small |"));
        assertTrue(md.contains("50%"));
        assertTrue(md.contains("**Overall**"));
    }

    @Test
    void overallPoolsRuns() {
        CaseResult all = ModelComparison.overall("m", List.of(r("a", 10, 5), r("b", 10, 10)));
        assertEquals(20, all.runs());
        assertEquals(15, all.passes());
    }
}
