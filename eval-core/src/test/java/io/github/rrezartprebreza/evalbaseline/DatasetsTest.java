package io.github.rrezartprebreza.evalbaseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetsTest {

    @TempDir
    Path dir;

    @Test
    void readsJsonlWithIdsEscapesAndNestedValues() throws IOException {
        Path file = dir.resolve("cases.jsonl");
        Files.writeString(file, """
                {"id": "refund", "question": "Can I get \\"cash\\"?\\nPlease", "days": 30, "tags": ["a", {"b": 1}], "note": null}

                // comments and blank lines are skipped
                {"question": "second"}
                """);

        List<EvalCase> cases = Datasets.read(file);

        assertEquals(2, cases.size());
        EvalCase first = cases.get(0);
        assertEquals("refund", first.id());
        assertEquals("Can I get \"cash\"?\nPlease", first.get("question"));
        assertEquals(30, first.getInt("days"));
        assertEquals("[\"a\", {\"b\": 1}]", first.get("tags"));
        assertNull(first.get("note"));
        assertEquals("row-2", cases.get(1).id());
    }

    @Test
    void readsCsvWithQuotedCommasQuotesAndLineBreaks() throws IOException {
        Path file = dir.resolve("cases.csv");
        Files.writeString(file, "id,question,expected\r\nq1,\"Hello, \"\"world\"\"\",yes\r\nq2,\"two\nlines\",no\r\n");

        List<EvalCase> cases = Datasets.read(file);

        assertEquals(List.of("q1", "q2"), cases.stream().map(EvalCase::id).toList());
        assertEquals("Hello, \"world\"", cases.get(0).get("question"));
        assertEquals("two\nlines", cases.get(1).get("question"));
        assertEquals("no", cases.get(1).get("expected"));
    }

    @Test
    void missingColumnFailsWithTheColumnNames() throws IOException {
        Path file = dir.resolve("cases.csv");
        Files.writeString(file, "question\nhello\n");
        EvalCase c = Datasets.read(file).get(0);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> c.get("answer"));
        assertEquals("Case row-1 has no column 'answer'. Columns: [question]", e.getMessage());
    }

    @Test
    void rejectsDuplicateIdsBadJsonAndUnknownFormats() throws IOException {
        Path dup = dir.resolve("dup.jsonl");
        Files.writeString(dup, "{\"id\":\"a\"}\n{\"id\":\"a\"}\n");
        assertThrows(IOException.class, () -> Datasets.read(dup));

        Path bad = dir.resolve("bad.jsonl");
        Files.writeString(bad, "{\"id\": oops}\n");
        IOException e = assertThrows(IOException.class, () -> Datasets.read(bad));
        assertEquals(true, e.getMessage().contains("line 1"));

        Path txt = dir.resolve("cases.txt");
        Files.writeString(txt, "x");
        assertThrows(IOException.class, () -> Datasets.read(txt));
    }

    @Test
    void readsAJsonArray() throws IOException {
        Path file = dir.resolve("cases.json");
        Files.writeString(file, """
                [
                  {"id": "refund", "question": "Can I get \\"cash\\"?", "meta": {"tags": [1, 2]}},
                  {"question": "second"}
                ]
                """);

        List<EvalCase> cases = Datasets.read(file);

        assertEquals(List.of("refund", "row-2"), cases.stream().map(EvalCase::id).toList());
        assertEquals("Can I get \"cash\"?", cases.get(0).get("question"));
        assertEquals("{\"tags\": [1, 2]}", cases.get(0).get("meta"));
    }

    @Test
    void rejectsEmptyAndMalformedJsonArrays() throws IOException {
        Path empty = dir.resolve("empty.json");
        Files.writeString(empty, "[]");
        assertThrows(IOException.class, () -> Datasets.read(empty));

        Path numbers = dir.resolve("numbers.json");
        Files.writeString(numbers, "[1, 2]");
        IOException e = assertThrows(IOException.class, () -> Datasets.read(numbers));
        assertEquals(true, e.getMessage().contains("must be an object"), e.getMessage());

        Path object = dir.resolve("object.json");
        Files.writeString(object, "{\"id\": \"a\"}");
        e = assertThrows(IOException.class, () -> Datasets.read(object));
        assertEquals(true, e.getMessage().contains("array of objects"), e.getMessage());
    }
}
