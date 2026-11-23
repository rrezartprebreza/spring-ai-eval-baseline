package io.github.rrezartprebreza.evalbaseline.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.github.rrezartprebreza.evalbaseline.Baseline;
import io.github.rrezartprebreza.evalbaseline.CaseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

class EvalExtensionTest {

    static final AtomicInteger CALLS = new AtomicInteger();

    /** Passes on every second run: a 50% pass rate. */
    static class HalfPassing {
        @EvalTest(runs = 10)
        void flaky(EvalTrial trial) {
            CALLS.incrementAndGet();
            trial.addCost(0.001);
            trial.check(trial.runIndex() % 2 == 0, "odd run");
        }
    }

    /** Uses a plain assertion, as most users will. */
    static class AlwaysPassing {
        @EvalTest(runs = 4, caseId = "always")
        void stable() {
            CALLS.incrementAndGet();
            assertTrue(true);
        }
    }

    /** Every run crashes: a broken test, not a weak model. */
    static class Broken {
        @EvalTest(runs = 3)
        void crashes() {
            throw new IllegalStateException("no recording");
        }
    }

    @TempDir
    Path dir;

    @BeforeEach
    void settings() {
        CALLS.set(0);
        System.setProperty("eval.baseline-dir", dir.resolve("baselines").toString());
        System.setProperty("eval.report-dir", dir.resolve("reports").toString());
    }

    @AfterEach
    void clear() {
        System.clearProperty("eval.baseline-dir");
        System.clearProperty("eval.report-dir");
        System.clearProperty("eval.update-baseline");
    }

    private static Events run(Class<?> c) {
        return EngineTestKit.engine("junit-jupiter").selectors(selectClass(c)).execute().testEvents();
    }

    private static Throwable failure(Events events) {
        return events.failed().stream().findFirst().orElseThrow()
                .getRequiredPayload(TestExecutionResult.class).getThrowable().orElseThrow();
    }

    @Test
    void runsTheMethodManyTimesAndPassesWithoutBaseline() throws Exception {
        run(HalfPassing.class).assertStatistics(s -> s.started(1).succeeded(1));
        assertEquals(10, CALLS.get());
        String report = Files.readString(dir.resolve("reports/HalfPassing.md"));
        assertTrue(report.contains("50%"), report);
        assertTrue(report.contains("NEW"), report);
    }

    @Test
    void failsWhenThePassRateDropsAgainstTheBaseline() throws Exception {
        Baseline.of(List.of(new CaseResult("flaky", 10, 10, -1, 0)))
                .write(dir.resolve("baselines/HalfPassing.tsv"));
        Events events = run(HalfPassing.class);
        events.assertStatistics(s -> s.failed(1));
        assertTrue(failure(events).getMessage().contains("PASS_RATE_DROP"));
    }

    @Test
    void updateFlagWritesTheBaseline() throws Exception {
        System.setProperty("eval.update-baseline", "true");
        run(HalfPassing.class).assertStatistics(s -> s.succeeded(1));
        CaseResult saved = Baseline.read(dir.resolve("baselines/HalfPassing.tsv")).get("flaky").orElseThrow();
        assertEquals(5, saved.passes());
        assertEquals(0.01, saved.totalCost(), 1e-9);
    }

    @Test
    void usesTheGivenCaseId() throws Exception {
        System.setProperty("eval.update-baseline", "true");
        run(AlwaysPassing.class).assertStatistics(s -> s.succeeded(1));
        assertEquals(4, CALLS.get());
        assertTrue(Baseline.read(dir.resolve("baselines/AlwaysPassing.tsv")).get("always").isPresent());
    }

    @Test
    void brokenTestFailsWithItsOwnError() {
        Events events = run(Broken.class);
        events.assertStatistics(s -> s.failed(1));
        assertTrue(failure(events) instanceof IllegalStateException);
    }
}
