package io.github.rrezartprebreza.evalbaseline.junit;

import io.github.rrezartprebreza.evalbaseline.Baseline;
import io.github.rrezartprebreza.evalbaseline.CaseResult;
import io.github.rrezartprebreza.evalbaseline.CostRecorder;
import io.github.rrezartprebreza.evalbaseline.Datasets;
import io.github.rrezartprebreza.evalbaseline.EvalCase;
import io.github.rrezartprebreza.evalbaseline.EvalGate;
import io.github.rrezartprebreza.evalbaseline.RegressionCheck;
import io.github.rrezartprebreza.evalbaseline.ScoreStats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

/** Runs {@link EvalTest} methods several times and checks them against the class baseline. */
public class EvalExtension implements InvocationInterceptor, ParameterResolver {

    private static final Object LOCK = new Object();

    /** Findings so far per test class, so the report file lists every case of the class. */
    private static final Map<Class<?>, List<RegressionCheck.Finding>> FINDINGS = new ConcurrentHashMap<>();

    @Override
    public boolean supportsParameter(ParameterContext p, ExtensionContext e) {
        Class<?> type = p.getParameter().getType();
        return type == EvalTrial.class || type == EvalCase.class;
    }

    @Override
    public Object resolveParameter(ParameterContext p, ExtensionContext e) {
        // Placeholders: the real objects are passed per run by interceptTestMethod.
        return p.getParameter().getType() == EvalTrial.class ? new EvalTrial(0, 1) : PLACEHOLDER_CASE;
    }

    private static final EvalCase PLACEHOLDER_CASE = new EvalCase("placeholder", Map.of());

    /** One planned run: its index and, in dataset mode, its row. */
    private record Plan(int index, EvalCase row) {
    }

    /** What one run produced. */
    private record Outcome(Plan plan, boolean passed, String reason, Throwable error, long millis, double cost,
                           double score) {
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ctx,
                                    ExtensionContext context) throws Throwable {
        Method method = ctx.getExecutable();
        EvalTest settings = method.getAnnotation(EvalTest.class);
        if (settings == null) {
            invocation.proceed();
            return;
        }
        invocation.skip();
        List<Plan> plans = plan(method, settings);
        method.setAccessible(true);
        Object target = ctx.getTarget().orElse(null);
        List<Object> resolved = ctx.getArguments();
        int total = plans.size();

        List<Outcome> outcomes = new ArrayList<>();
        if (settings.parallel() <= 1) {
            for (Plan plan : plans) {
                outcomes.add(runOnce(method, target, resolved, plan, total));
            }
        } else {
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(settings.parallel(), total));
            try {
                List<Future<Outcome>> futures = new ArrayList<>();
                for (Plan plan : plans) {
                    futures.add(pool.submit(() -> runOnce(method, target, resolved, plan, total)));
                }
                for (Future<Outcome> f : futures) {
                    try {
                        outcomes.add(f.get());
                    } catch (ExecutionException e) {
                        throw e.getCause();
                    }
                }
            } finally {
                pool.shutdownNow();
            }
        }

        int passes = 0;
        int errors = 0;
        Throwable firstError = null;
        double cost = 0;
        ScoreStats scores = ScoreStats.NONE;
        long[] millis = new long[total];
        List<String> failedRows = new ArrayList<>();
        for (int i = 0; i < outcomes.size(); i++) {
            Outcome o = outcomes.get(i);
            millis[i] = o.millis();
            cost += o.cost();
            if (!Double.isNaN(o.score())) {
                scores = scores.plus(o.score());
            }
            if (o.passed()) {
                passes++;
            } else if (o.plan().row() != null) {
                failedRows.add(o.plan().row().id() + (o.reason() == null ? "" : ": " + o.reason()));
            }
            if (o.error() != null) {
                errors++;
                if (firstError == null) {
                    firstError = o.error();
                }
            }
        }
        // Every run crashed the same way: the test is broken (no recording, wrong setup), not the model.
        if (errors == total) {
            throw firstError;
        }

        String caseId = settings.caseId().isBlank() ? method.getName() : settings.caseId();
        CaseResult result = new CaseResult(caseId, total, passes, CaseResult.p95(millis), cost, scores);
        check(context.getRequiredTestClass(), result, failedRows);
    }

    private static List<Plan> plan(Method method, EvalTest settings) {
        EvalDataset dataset = method.getAnnotation(EvalDataset.class);
        List<Plan> plans = new ArrayList<>();
        if (dataset == null) {
            if (settings.runs() <= 0) {
                throw new IllegalArgumentException("@EvalTest runs must be positive on " + method);
            }
            for (int i = 0; i < settings.runs(); i++) {
                plans.add(new Plan(i, null));
            }
            return plans;
        }
        if (dataset.repeat() <= 0) {
            throw new IllegalArgumentException("@EvalDataset repeat must be positive on " + method);
        }
        List<EvalCase> rows;
        try {
            rows = Datasets.read(Path.of(dataset.value()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read dataset for " + method.getName() + ": " + e.getMessage(), e);
        }
        for (EvalCase row : rows) {
            for (int r = 0; r < dataset.repeat(); r++) {
                plans.add(new Plan(plans.size(), row));
            }
        }
        return plans;
    }

    private static Outcome runOnce(Method method, Object target, List<Object> resolved, Plan plan, int total) {
        EvalTrial trial = new EvalTrial(plan.index(), total);
        Object[] args = resolved.toArray();
        for (int a = 0; a < args.length; a++) {
            if (args[a] instanceof EvalTrial) {
                args[a] = trial;
            } else if (args[a] instanceof EvalCase) {
                args[a] = plan.row() == null ? PLACEHOLDER_CASE : plan.row();
            }
        }
        long start = System.nanoTime();
        boolean passed;
        String reason = null;
        Throwable error = null;
        CostRecorder.start();
        double recorded;
        try {
            method.invoke(target, args);
            passed = !trial.failed();
            reason = trial.failure();
        } catch (InvocationTargetException wrapped) {
            Throwable t = wrapped.getCause();
            passed = false;
            reason = t.getMessage();
            if (!(t instanceof AssertionError)) {
                error = t;
            }
        } catch (IllegalAccessException e) {
            passed = false;
            error = e;
        } finally {
            recorded = CostRecorder.stop();
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        return new Outcome(plan, passed, reason, error, millis, trial.cost() + recorded, trial.scoreValue());
    }

    private static void check(Class<?> testClass, CaseResult result, List<String> failedRows) {
        EvalBaseline config = testClass.getAnnotation(EvalBaseline.class);
        RegressionCheck.Options options = config == null ? RegressionCheck.Options.defaults()
                : new RegressionCheck.Options(config.alpha(), config.minDrop(), config.maxLatencyFactor(), config.maxCostPerRun());
        Path file = config == null || config.value().isBlank()
                ? EvalGate.baselineDir().resolve(testClass.getSimpleName() + ".tsv")
                : Path.of(config.value());
        boolean update = EvalGate.updateBaseline();

        RegressionCheck.Report single;
        synchronized (LOCK) {
            try {
                Baseline baseline = Baseline.read(file);
                single = RegressionCheck.compare(baseline, List.of(result), options);
                List<RegressionCheck.Finding> all = FINDINGS.computeIfAbsent(testClass, k -> new ArrayList<>());
                all.removeIf(f -> f.current().caseId().equals(result.caseId()));
                all.addAll(single.findings());
                EvalGate.writeReport(testClass.getSimpleName(), new RegressionCheck.Report(List.copyOf(all)), update);
                if (update) {
                    EvalGate.merge(baseline, List.of(result)).write(file);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        if (!update && single.failed()) {
            throw new AssertionError(single.toMarkdown() + failedRowsText(failedRows)
                    + "\nIf this change is intended, run again with -Deval.update-baseline=true and commit " + file + ".");
        }
    }

    private static String failedRowsText(List<String> failedRows) {
        if (failedRows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\nFailed rows (").append(failedRows.size()).append("):\n");
        failedRows.stream().limit(20).forEach(r -> sb.append("- ").append(r.replace('\n', ' ')).append('\n'));
        if (failedRows.size() > 20) {
            sb.append("- ...\n");
        }
        return sb.toString();
    }
}
