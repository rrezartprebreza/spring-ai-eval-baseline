package io.github.rrezartprebreza.evalbaseline.junit;

import io.github.rrezartprebreza.evalbaseline.Baseline;
import io.github.rrezartprebreza.evalbaseline.CaseResult;
import io.github.rrezartprebreza.evalbaseline.EvalGate;
import io.github.rrezartprebreza.evalbaseline.RegressionCheck;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
        return p.getParameter().getType() == EvalTrial.class;
    }

    @Override
    public Object resolveParameter(ParameterContext p, ExtensionContext e) {
        return new EvalTrial(0, 1);
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
        if (settings.runs() <= 0) {
            throw new IllegalArgumentException("@EvalTest runs must be positive on " + method);
        }
        method.setAccessible(true);
        Object target = ctx.getTarget().orElse(null);
        List<Object> resolved = ctx.getArguments();

        int passes = 0;
        int errors = 0;
        Throwable firstError = null;
        double cost = 0;
        long[] millis = new long[settings.runs()];
        for (int i = 0; i < settings.runs(); i++) {
            EvalTrial trial = new EvalTrial(i, settings.runs());
            Object[] args = resolved.toArray();
            for (int a = 0; a < args.length; a++) {
                if (args[a] instanceof EvalTrial) {
                    args[a] = trial;
                }
            }
            long start = System.nanoTime();
            boolean passed;
            try {
                method.invoke(target, args);
                passed = !trial.failed();
            } catch (InvocationTargetException wrapped) {
                Throwable t = wrapped.getCause();
                passed = false;
                if (!(t instanceof AssertionError)) {
                    errors++;
                    if (firstError == null) {
                        firstError = t;
                    }
                }
            }
            millis[i] = (System.nanoTime() - start) / 1_000_000;
            cost += trial.cost();
            if (passed) {
                passes++;
            }
        }
        // Every run crashed the same way: the test is broken (no recording, wrong setup), not the model.
        if (errors == settings.runs()) {
            throw firstError;
        }

        String caseId = settings.caseId().isBlank() ? method.getName() : settings.caseId();
        CaseResult result = new CaseResult(caseId, settings.runs(), passes, CaseResult.p95(millis), cost);
        check(context.getRequiredTestClass(), result);
    }

    private static void check(Class<?> testClass, CaseResult result) {
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
            throw new AssertionError(single.toMarkdown()
                    + "\nIf this change is intended, run again with -Deval.update-baseline=true and commit " + file + ".");
        }
    }
}
