package io.github.rrezartprebreza.evalbaseline;

/**
 * Runs one eval case several times and counts passes, latency and cost.
 *
 * <pre>{@code
 * CaseResult r = RepeatedRunner.run("refund-question", 10, () -> {
 *     String answer = bot.answer("Can I get a refund?");
 *     return Trial.of(answer.contains("30 days"));
 * });
 * }</pre>
 *
 * An exception thrown by a trial counts as a failed run, not as a crash of the test,
 * because a model that times out has failed that run.
 */
public final class RepeatedRunner {

    /** One run of a case. */
    @FunctionalInterface
    public interface TrialFunction {
        Trial run() throws Exception;
    }

    /** Result of one run: whether it passed and what it cost. */
    public record Trial(boolean passed, double cost) {
        public static Trial of(boolean passed) {
            return new Trial(passed, 0);
        }

        public static Trial of(boolean passed, double cost) {
            return new Trial(passed, cost);
        }
    }

    private RepeatedRunner() {
    }

    public static CaseResult run(String caseId, int runs, TrialFunction trial) {
        if (runs <= 0) {
            throw new IllegalArgumentException("runs must be positive");
        }
        int passes = 0;
        double cost = 0;
        long[] millis = new long[runs];
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            try {
                Trial t = trial.run();
                if (t != null) {
                    if (t.passed()) {
                        passes++;
                    }
                    cost += t.cost();
                }
            } catch (Exception e) {
                // counted as a failed run
            }
            millis[i] = (System.nanoTime() - start) / 1_000_000;
        }
        return new CaseResult(caseId, runs, passes, CaseResult.p95(millis), cost);
    }
}
