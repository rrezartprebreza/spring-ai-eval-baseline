package io.github.rrezartprebreza.evalbaseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelComparisonTest {

    @AfterEach
    void clear() {
        System.clearProperty("eval.report-dir");
    }

    @Test
    void writesTheComparisonTableToTheReportFolder(@TempDir Path dir) throws Exception {
        System.setProperty("eval.report-dir", dir.toString());
        Map<String, List<CaseResult>> byModel = new LinkedHashMap<>();
        byModel.put("small-model", List.of(new CaseResult("refund", 10, 6, -1, 0.001)));
        byModel.put("large-model", List.of(new CaseResult("refund", 10, 10, -1, 0.01)));

        Path file = ModelComparison.writeReport("support bot models", byModel);

        assertEquals(dir.resolve("support_bot_models.md"), file);
        String report = Files.readString(file);
        assertTrue(report.startsWith("### support bot models\n\n| Case | small-model | large-model |"), report);
        assertTrue(report.contains("| refund | 60%"), report);
        assertTrue(report.contains("| **Overall** |"), report);
    }
}
