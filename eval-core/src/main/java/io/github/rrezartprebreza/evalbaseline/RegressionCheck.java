package io.github.rrezartprebreza.evalbaseline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Compares new results with a baseline and decides which cases got worse.
 *
 * <p>A pass-rate drop fails a case only when it is both large enough ({@link Options#minDrop})
 * and unlikely to be chance (one-sided Fisher exact test, p below {@link Options#alpha}).
 * So a test that passes 9 of 10 times and then 8 of 10 times does not break the build.
 */
public final class RegressionCheck {

    /**
     * @param alpha               significance level for a pass-rate drop, for example 0.05
     * @param minDrop             smallest drop in pass rate that counts, for example 0.10
     * @param maxLatencyFactor    fail when p95 latency grows past baseline times this factor; 0 turns it off
     * @param maxCostPerRun       fail when cost per run is above this; 0 turns it off
     */
    public record Options(double alpha, double minDrop, double maxLatencyFactor, double maxCostPerRun) {
        public static Options defaults() {
            return new Options(0.05, 0.10, 1.5, 0);
        }

        public Options withAlpha(double a) {
            return new Options(a, minDrop, maxLatencyFactor, maxCostPerRun);
        }

        public Options withMinDrop(double d) {
            return new Options(alpha, d, maxLatencyFactor, maxCostPerRun);
        }

        public Options withMaxLatencyFactor(double f) {
            return new Options(alpha, minDrop, f, maxCostPerRun);
        }

        public Options withMaxCostPerRun(double c) {
            return new Options(alpha, minDrop, maxLatencyFactor, c);
        }
    }

    public enum Status { OK, NEW, PASS_RATE_DROP, SLOWER, OVER_BUDGET }

    /** Verdict for one case. {@code pValue} is NaN when no test was run. */
    public record Finding(CaseResult current, CaseResult baseline, Status status, double pValue) {
        public boolean failed() {
            return status == Status.PASS_RATE_DROP || status == Status.SLOWER || status == Status.OVER_BUDGET;
        }
    }

    /** All findings of one comparison. */
    public record Report(List<Finding> findings) {
        public boolean failed() {
            return findings.stream().anyMatch(Finding::failed);
        }

        /** A Markdown table, ready to post as a pull request comment. */
        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append(failed() ? "**Eval regression found.**\n\n" : "**No eval regression.**\n\n");
            sb.append("| Case | Pass rate (95% CI) | Baseline | p | p95 ms | Status |\n");
            sb.append("| --- | --- | --- | --- | --- | --- |\n");
            for (Finding f : findings) {
                CaseResult c = f.current();
                Interval ci = c.interval();
                sb.append("| ").append(c.caseId())
                        .append(" | ").append(pct(c.passRate())).append(" (").append(pct(ci.low())).append("–")
                        .append(pct(ci.high())).append(") ")
                        .append(" | ").append(f.baseline() == null ? "–" : pct(f.baseline().passRate()))
                        .append(" | ").append(Double.isNaN(f.pValue()) ? "–" : String.format(Locale.ROOT, "%.3f", f.pValue()))
                        .append(" | ").append(c.p95Millis() < 0 ? "–" : String.valueOf(c.p95Millis()))
                        .append(" | ").append(f.status()).append(" |\n");
            }
            return sb.toString();
        }

        private static String pct(double v) {
            return Math.round(v * 100) + "%";
        }
    }

    private RegressionCheck() {
    }

    public static Report compare(Baseline baseline, Collection<CaseResult> current, Options options) {
        List<Finding> findings = new ArrayList<>();
        for (CaseResult c : current) {
            CaseResult b = baseline.get(c.caseId()).orElse(null);
            findings.add(judge(c, b, options));
        }
        return new Report(List.copyOf(findings));
    }

    static Finding judge(CaseResult c, CaseResult b, Options o) {
        if (o.maxCostPerRun() > 0 && c.costPerRun() > o.maxCostPerRun()) {
            return new Finding(c, b, Status.OVER_BUDGET, Double.NaN);
        }
        if (b == null) {
            return new Finding(c, null, Status.NEW, Double.NaN);
        }
        double p = Double.NaN;
        double drop = b.passRate() - c.passRate();
        if (drop >= o.minDrop() - 1e-9) {
            p = FisherExact.pLower(b.passes(), b.runs(), c.passes(), c.runs());
            if (p < o.alpha()) {
                return new Finding(c, b, Status.PASS_RATE_DROP, p);
            }
        }
        if (o.maxLatencyFactor() > 0 && b.p95Millis() > 0 && c.p95Millis() > b.p95Millis() * o.maxLatencyFactor()) {
            return new Finding(c, b, Status.SLOWER, p);
        }
        return new Finding(c, b, Status.OK, p);
    }

    /** Throws an {@link AssertionError} with the Markdown table when any case regressed. */
    public static Report assertNoRegression(Baseline baseline, Collection<CaseResult> current, Options options) {
        Report report = compare(baseline, current, options);
        if (report.failed()) {
            throw new AssertionError(report.toMarkdown());
        }
        return report;
    }
}
