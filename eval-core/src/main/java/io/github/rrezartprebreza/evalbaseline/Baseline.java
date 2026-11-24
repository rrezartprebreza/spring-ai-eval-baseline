package io.github.rrezartprebreza.evalbaseline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The last accepted results, stored as a small tab-separated file in the repository so a
 * change to it shows up in code review.
 *
 * <pre>
 * caseId	runs	passes	p95Millis	totalCost
 * refund-question	10	9	1840	0.0042
 * </pre>
 *
 * When any case reports scores, three columns are added: {@code scoreCount}, {@code scoreSum}
 * and {@code scoreSumSq}. Files with five columns stay valid.
 */
public final class Baseline {

    static final String HEADER = "caseId\truns\tpasses\tp95Millis\ttotalCost";
    static final String SCORE_COLUMNS = "\tscoreCount\tscoreSum\tscoreSumSq";

    private final Map<String, CaseResult> cases;

    private Baseline(Map<String, CaseResult> cases) {
        this.cases = cases;
    }

    public static Baseline of(Collection<CaseResult> results) {
        Map<String, CaseResult> map = new LinkedHashMap<>();
        for (CaseResult r : results) {
            if (map.put(r.caseId(), r) != null) {
                throw new IllegalArgumentException("duplicate caseId: " + r.caseId());
            }
        }
        return new Baseline(map);
    }

    public static Baseline empty() {
        return new Baseline(new LinkedHashMap<>());
    }

    /** Reads a baseline file; a missing file gives an empty baseline. */
    public static Baseline read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return empty();
        }
        List<CaseResult> results = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("caseId\t")) {
                continue;
            }
            String[] f = line.split("\t");
            if (f.length != 5 && f.length != 8) {
                throw new IOException(file + " line " + (i + 1) + ": expected 5 or 8 tab-separated fields");
            }
            try {
                ScoreStats scores = f.length == 8
                        ? new ScoreStats(Integer.parseInt(f[5]), Double.parseDouble(f[6]), Double.parseDouble(f[7]))
                        : ScoreStats.NONE;
                results.add(new CaseResult(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                        Long.parseLong(f[3]), Double.parseDouble(f[4]), scores));
            } catch (IllegalArgumentException e) {
                throw new IOException(file + " line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return of(results);
    }

    public void write(Path file) throws IOException {
        boolean withScores = cases.values().stream().anyMatch(r -> r.scores().present());
        StringBuilder sb = new StringBuilder(HEADER).append(withScores ? SCORE_COLUMNS : "").append('\n');
        for (CaseResult r : cases.values()) {
            sb.append(r.caseId()).append('\t').append(r.runs()).append('\t').append(r.passes()).append('\t')
                    .append(r.p95Millis()).append('\t').append(r.totalCost());
            if (withScores) {
                ScoreStats sc = r.scores();
                sb.append('\t').append(sc.count()).append('\t').append(sc.sum()).append('\t').append(sc.sumSq());
            }
            sb.append('\n');
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    public Optional<CaseResult> get(String caseId) {
        return Optional.ofNullable(cases.get(caseId));
    }

    public Collection<CaseResult> cases() {
        return List.copyOf(cases.values());
    }
}
