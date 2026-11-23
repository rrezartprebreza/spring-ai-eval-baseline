package io.github.rrezartprebreza.evalbaseline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One call that does the usual end of an eval run: compare with the baseline, save the report,
 * and either fail on a regression or, when asked, accept the results as the new baseline.
 *
 * <pre>{@code
 * EvalGate.verify("support-bot", Path.of("src/test/resources/baseline.tsv"), results, Options.defaults());
 * }</pre>
 *
 * <p>System properties (or environment variables in capitals with underscores):
 * <ul>
 *   <li>{@code eval.update-baseline=true} writes the results to the baseline file instead of failing;</li>
 *   <li>{@code eval.report-dir} sets where Markdown reports go (default {@code target/eval-reports}).</li>
 * </ul>
 */
public final class EvalGate {

    private EvalGate() {
    }

    public static RegressionCheck.Report verify(String name, Path baselineFile, Collection<CaseResult> results,
                                                RegressionCheck.Options options) {
        try {
            Baseline baseline = Baseline.read(baselineFile);
            RegressionCheck.Report report = RegressionCheck.compare(baseline, results, options);
            boolean update = updateBaseline();
            writeReport(name, report, update);
            if (update) {
                merge(baseline, results).write(baselineFile);
                return report;
            }
            if (report.failed()) {
                throw new AssertionError(report.toMarkdown()
                        + "\nIf this change is intended, run again with -Deval.update-baseline=true and commit "
                        + baselineFile + ".");
            }
            return report;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Keeps baseline cases that did not run this time, replaces the ones that did. */
    public static Baseline merge(Baseline old, Collection<CaseResult> results) {
        Map<String, CaseResult> map = new LinkedHashMap<>();
        for (CaseResult r : old.cases()) {
            map.put(r.caseId(), r);
        }
        for (CaseResult r : results) {
            map.put(r.caseId(), r);
        }
        return Baseline.of(map.values());
    }

    public static boolean updateBaseline() {
        return Boolean.parseBoolean(setting("eval.update-baseline", "false"));
    }

    /** Folder for default baseline files; {@code eval.baseline-dir}, default {@code src/test/resources/eval-baselines}. */
    public static Path baselineDir() {
        return Path.of(setting("eval.baseline-dir", "src/test/resources/eval-baselines"));
    }

    public static Path reportDir() {
        return Path.of(setting("eval.report-dir", "target/eval-reports"));
    }

    static String setting(String property, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(property.toUpperCase(java.util.Locale.ROOT).replace('.', '_').replace('-', '_'));
        }
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    /** Writes {@code <report-dir>/<name>.md}, replacing an older report of the same name. */
    public static void writeReport(String name, RegressionCheck.Report report, boolean update) throws IOException {
        Path dir = reportDir();
        Files.createDirectories(dir);
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        String body = "### " + name + (update ? " (baseline updated)" : "") + "\n\n" + report.toMarkdown();
        Files.writeString(dir.resolve(safe + ".md"), body, StandardCharsets.UTF_8);
    }
}
