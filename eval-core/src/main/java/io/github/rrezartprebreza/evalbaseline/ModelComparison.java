package io.github.rrezartprebreza.evalbaseline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Runs the same cases against several models and puts the pass rates side by side.
 *
 * <pre>{@code
 * Map<String, List<CaseResult>> byModel = ModelComparison.run(
 *         List.of("llama-3.1-8b-instant", "llama-3.3-70b-versatile"),
 *         model -> List.of(RepeatedRunner.run("refund", 10, () -> trialFor(model))));
 * System.out.println(ModelComparison.toMarkdown(byModel));
 * }</pre>
 */
public final class ModelComparison {

    private ModelComparison() {
    }

    public static Map<String, List<CaseResult>> run(List<String> models, Function<String, List<CaseResult>> evalForModel) {
        Map<String, List<CaseResult>> out = new LinkedHashMap<>();
        for (String model : models) {
            out.put(model, List.copyOf(evalForModel.apply(model)));
        }
        return out;
    }

    /** Overall pass rate of one model across all its cases. */
    public static CaseResult overall(String model, List<CaseResult> results) {
        int runs = 0;
        int passes = 0;
        long p95 = -1;
        double cost = 0;
        ScoreStats scores = ScoreStats.NONE;
        for (CaseResult r : results) {
            runs += r.runs();
            passes += r.passes();
            p95 = Math.max(p95, r.p95Millis());
            cost += r.totalCost();
            scores = scores.plus(r.scores());
        }
        if (runs == 0) {
            throw new IllegalArgumentException("no results for " + model);
        }
        return new CaseResult(model, runs, passes, p95, cost, scores);
    }

    /** One row per case, one column per model, plus an overall row with cost per run. */
    public static String toMarkdown(Map<String, List<CaseResult>> byModel) {
        List<String> models = new ArrayList<>(byModel.keySet());
        Set<String> caseIds = new LinkedHashSet<>();
        byModel.values().forEach(list -> list.forEach(r -> caseIds.add(r.caseId())));

        StringBuilder sb = new StringBuilder("| Case |");
        models.forEach(m -> sb.append(' ').append(m).append(" |"));
        sb.append("\n| --- |");
        models.forEach(m -> sb.append(" --- |"));
        sb.append('\n');
        for (String id : caseIds) {
            sb.append("| ").append(id).append(" |");
            for (String m : models) {
                CaseResult r = byModel.get(m).stream().filter(c -> c.caseId().equals(id)).findFirst().orElse(null);
                sb.append(' ').append(r == null ? "–" : cell(r)).append(" |");
            }
            sb.append('\n');
        }
        sb.append("| **Overall** |");
        for (String m : models) {
            sb.append(" **").append(cell(overall(m, byModel.get(m)))).append("** |");
        }
        sb.append("\n| Cost per run |");
        for (String m : models) {
            sb.append(' ').append(String.format(java.util.Locale.ROOT, "%.5f", overall(m, byModel.get(m)).costPerRun())).append(" |");
        }
        return sb.append('\n').toString();
    }

    /**
     * Writes {@link #toMarkdown} to {@code <eval.report-dir>/<name>.md} (default
     * {@code target/eval-reports}), where the build workflow collects reports for the CI summary
     * and the pull request comment.
     *
     * @return the file written
     */
    public static java.nio.file.Path writeReport(String name, Map<String, List<CaseResult>> byModel)
            throws java.io.IOException {
        java.nio.file.Path dir = EvalGate.reportDir();
        java.nio.file.Files.createDirectories(dir);
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        java.nio.file.Path file = dir.resolve(safe + ".md");
        java.nio.file.Files.writeString(file, "### " + name + "\n\n" + toMarkdown(byModel),
                java.nio.charset.StandardCharsets.UTF_8);
        return file;
    }

    private static String cell(CaseResult r) {
        Interval ci = r.interval();
        return pct(r.passRate()) + " (" + pct(ci.low()) + "–" + pct(ci.high()) + ")";
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }
}
