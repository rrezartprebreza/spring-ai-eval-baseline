package io.github.rrezartprebreza.evalbaseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.rrezartprebreza.evalbaseline.RegressionCheck.Options;
import io.github.rrezartprebreza.evalbaseline.RegressionCheck.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScoresTest {

    private static final ScoreStats HIGH = ScoreStats.of(0.9, 0.8, 0.85, 0.95, 0.9, 0.88, 0.92, 0.87, 0.9, 0.86);
    private static final ScoreStats LOW = ScoreStats.of(0.6, 0.7, 0.65, 0.55, 0.7, 0.6, 0.68, 0.62, 0.66, 0.64);

    @Test
    void studentDistributionMatchesTables() {
        assertEquals(0.963306, Stats.studentCdf(2.0, 10), 1e-5);
        assertEquals(0.096952, Stats.studentCdf(-1.5, 5), 1e-5);
        assertEquals(0.5, Stats.studentCdf(0, 3), 1e-12);
    }

    @Test
    void significantScoreDropFails() {
        Baseline base = Baseline.of(List.of(new CaseResult("a", 10, 10, -1, 0, HIGH)));
        var finding = RegressionCheck.compare(base, List.of(new CaseResult("a", 10, 10, -1, 0, LOW)), Options.defaults())
                .findings().get(0);
        assertEquals(Status.SCORE_DROP, finding.status());
    }

    @Test
    void smallScoreDropPasses() {
        ScoreStats slightlyLower = ScoreStats.of(0.85, 0.8, 0.82, 0.9, 0.88, 0.84, 0.9, 0.83, 0.86, 0.85);
        Baseline base = Baseline.of(List.of(new CaseResult("a", 10, 10, -1, 0, HIGH)));
        var report = RegressionCheck.compare(base, List.of(new CaseResult("a", 10, 10, -1, 0, slightlyLower)), Options.defaults());
        assertEquals(Status.OK, report.findings().get(0).status());
    }

    @Test
    void scoresRoundTripThroughTheBaselineFileAndOldFilesStillRead(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("b.tsv");
        Baseline.of(List.of(new CaseResult("a", 10, 9, 5, 0, HIGH), new CaseResult("b", 3, 3, -1, 0))).write(file);
        Baseline back = Baseline.read(file);
        assertEquals(HIGH, back.get("a").orElseThrow().scores());
        assertEquals(ScoreStats.NONE, back.get("b").orElseThrow().scores());

        Path old = dir.resolve("old.tsv");
        Files.writeString(old, "caseId\truns\tpasses\tp95Millis\ttotalCost\nx\t10\t9\t100\t0.0\n");
        assertEquals(9, Baseline.read(old).get("x").orElseThrow().passes());
    }

    @Test
    void scoresMustBeBetweenZeroAndOne() {
        assertThrows(IllegalArgumentException.class, () -> ScoreStats.of(1.2));
        assertThrows(IllegalArgumentException.class, () -> ScoreStats.of(Double.NaN));
    }

    @Test
    void repeatedRunnerCollectsScores() {
        CaseResult r = RepeatedRunner.run("s", 4, () -> RepeatedRunner.Trial.scored(0.5));
        assertEquals(4, r.scores().count());
        assertEquals(0.5, r.scores().mean(), 1e-12);
        assertEquals(4, r.passes());
    }

    @Test
    void costRecorderCountsOnlyInsideARun() {
        CostRecorder.add(5);
        CostRecorder.start();
        CostRecorder.add(0.25);
        CostRecorder.add(0.5);
        assertEquals(0.75, CostRecorder.stop(), 1e-12);
        assertEquals(0, CostRecorder.stop(), 1e-12);
    }
}
