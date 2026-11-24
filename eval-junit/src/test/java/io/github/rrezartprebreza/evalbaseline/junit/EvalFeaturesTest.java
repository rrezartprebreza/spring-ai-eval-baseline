package io.github.rrezartprebreza.evalbaseline.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.github.rrezartprebreza.evalbaseline.Baseline;
import io.github.rrezartprebreza.evalbaseline.CaseResult;
import io.github.rrezartprebreza.evalbaseline.CostRecorder;
import io.github.rrezartprebreza.evalbaseline.EvalCase;
import io.github.rrezartprebreza.evalbaseline.ScoreStats;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

class EvalFeaturesTest {

    static final Map<String, String> ANSWERS = Map.of(
            "France", "Paris", "Italy", "Rome", "Spain", "Barcelona", "Kosovo", "Pristina");
    static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    static final Set<String> THREADS = ConcurrentHashMap.newKeySet();
    static final AtomicInteger CALLS = new AtomicInteger();

    /** One run per dataset row; the fake model gets Spain wrong. */
    static class Capitals {
        @EvalTest
        @EvalDataset("src/test/resources/capitals.jsonl")
        void knowsTheCapital(EvalCase c) {
            SEEN.add(c.id());
            CostRecorder.add(0.002);
            assertEquals(c.get("capital"), ANSWERS.get(c.get("country")));
        }
    }

    /** Each row twice. */
    static class CapitalsRepeated {
        @EvalTest
        @EvalDataset(value = "src/test/resources/capitals.jsonl", repeat = 2)
        void knowsTheCapital(EvalCase c) {
            CALLS.incrementAndGet();
        }
    }

    /** Scores instead of pass/fail. */
    static class Scored {
        @EvalTest(runs = 10)
        void similarity(EvalTrial trial) {
            trial.score(trial.runIndex() % 2 == 0 ? 0.4 : 0.6);
        }
    }

    /** Runs on several threads at once. */
    static class Parallel {
        @EvalTest(runs = 8, parallel = 4)
        void slow() throws InterruptedException {
            THREADS.add(Thread.currentThread().getName());
            CALLS.incrementAndGet();
            Thread.sleep(50);
        }
    }

    @TempDir
    Path dir;

    @BeforeEach
    void settings() {
        SEEN.clear();
        THREADS.clear();
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
    void datasetRunsOncePerRowAndRecordsCost() throws Exception {
        System.setProperty("eval.update-baseline", "true");
        run(Capitals.class).assertStatistics(s -> s.succeeded(1));
        assertEquals(Set.of("france", "italy", "spain", "kosovo"), SEEN);
        CaseResult saved = Baseline.read(dir.resolve("baselines/Capitals.tsv")).get("knowsTheCapital").orElseThrow();
        assertEquals(4, saved.runs());
        assertEquals(3, saved.passes());
        assertEquals(0.008, saved.totalCost(), 1e-9);
    }

    @Test
    void datasetFailureListsTheFailedRows() throws Exception {
        Baseline.of(List.of(new CaseResult("knowsTheCapital", 40, 40, -1, 0)))
                .write(dir.resolve("baselines/Capitals.tsv"));
        Events events = run(Capitals.class);
        events.assertStatistics(s -> s.failed(1));
        String message = failure(events).getMessage();
        assertTrue(message.contains("Failed rows (1)"), message);
        assertTrue(message.contains("- spain"), message);
    }

    @Test
    void repeatRunsEachRowSeveralTimes() {
        run(CapitalsRepeated.class).assertStatistics(s -> s.succeeded(1));
        assertEquals(8, CALLS.get());
    }

    @Test
    void scoresAreSavedAndADropFails() throws Exception {
        System.setProperty("eval.update-baseline", "true");
        run(Scored.class).assertStatistics(s -> s.succeeded(1));
        CaseResult saved = Baseline.read(dir.resolve("baselines/Scored.tsv")).get("similarity").orElseThrow();
        assertEquals(10, saved.scores().count());
        assertEquals(0.5, saved.scores().mean(), 1e-9);

        System.clearProperty("eval.update-baseline");
        Baseline.of(List.of(new CaseResult("similarity", 10, 10, -1, 0,
                ScoreStats.of(0.9, 0.95, 0.9, 0.85, 0.9, 0.95, 0.9, 0.88, 0.92, 0.9))))
                .write(dir.resolve("baselines/Scored.tsv"));
        Events events = run(Scored.class);
        events.assertStatistics(s -> s.failed(1));
        assertTrue(failure(events).getMessage().contains("SCORE_DROP"));
    }

    @Test
    void parallelRunsUseSeveralThreads() {
        run(Parallel.class).assertStatistics(s -> s.succeeded(1));
        assertEquals(8, CALLS.get());
        assertTrue(THREADS.size() > 1, "threads: " + THREADS);
    }
}
