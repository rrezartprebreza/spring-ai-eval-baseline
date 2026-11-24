package io.github.rrezartprebreza.evalbaseline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads eval cases from a file, one case per row.
 *
 * <ul>
 *   <li><b>JSONL</b> ({@code .jsonl}, {@code .ndjson}): one JSON object per line. Strings, numbers
 *       and booleans become text; nested arrays and objects are kept as JSON text.</li>
 *   <li><b>JSON</b> ({@code .json}): one array of flat objects, in the same form as JSONL rows.</li>
 *   <li><b>CSV</b> ({@code .csv}): a header row, then one row per case. Fields may be quoted with
 *       {@code "}, and a quoted field may contain commas, quotes ({@code ""}) and line breaks.</li>
 * </ul>
 *
 * A column named {@code id} gives the case id; otherwise cases are named {@code row-1},
 * {@code row-2} and so on. Blank lines and JSONL lines starting with {@code //} are skipped.
 */
public final class Datasets {

    private Datasets() {
    }

    public static List<EvalCase> read(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        List<EvalCase> cases;
        if (name.endsWith(".jsonl") || name.endsWith(".ndjson")) {
            cases = readJsonl(text, file);
        } else if (name.endsWith(".json")) {
            cases = readJsonArray(text, file);
        } else if (name.endsWith(".csv")) {
            cases = readCsv(text, file);
        } else {
            throw new IOException("Unknown dataset format for " + file + ": use .jsonl, .json or .csv");
        }
        Set<String> ids = new HashSet<>();
        for (EvalCase c : cases) {
            if (!ids.add(c.id())) {
                throw new IOException(file + ": duplicate case id '" + c.id() + "'");
            }
        }
        if (cases.isEmpty()) {
            throw new IOException(file + " holds no cases");
        }
        return List.copyOf(cases);
    }

    static List<EvalCase> readJsonl(String text, Path file) throws IOException {
        List<EvalCase> out = new ArrayList<>();
        String[] lines = text.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            try {
                Map<String, String> values = new FlatJson(line).object();
                out.add(new EvalCase(idOf(values, out.size() + 1), values));
            } catch (IllegalArgumentException e) {
                throw new IOException(file + " line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return out;
    }

    static List<EvalCase> readJsonArray(String text, Path file) throws IOException {
        List<EvalCase> out = new ArrayList<>();
        try {
            for (Map<String, String> values : new FlatJson(text).array()) {
                out.add(new EvalCase(idOf(values, out.size() + 1), values));
            }
        } catch (IllegalArgumentException e) {
            throw new IOException(file + ": " + e.getMessage(), e);
        }
        return out;
    }

    static List<EvalCase> readCsv(String text, Path file) throws IOException {
        List<List<String>> rows = csvRows(text, file);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> header = rows.get(0);
        List<EvalCase> out = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.size() == 1 && row.get(0).isBlank()) {
                continue;
            }
            if (row.size() != header.size()) {
                throw new IOException(file + " row " + (r + 1) + ": expected " + header.size() + " fields, found " + row.size());
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                values.put(header.get(c).strip(), row.get(c));
            }
            out.add(new EvalCase(idOf(values, out.size() + 1), values));
        }
        return out;
    }

    private static String idOf(Map<String, String> values, int rowNumber) {
        String id = values.get("id");
        return id == null || id.isBlank() ? "row-" + rowNumber : id.strip();
    }

    private static List<List<String>> csvRows(String text, Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            any = true;
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"' && field.isEmpty()) {
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                any = false;
            } else {
                field.append(ch);
            }
        }
        if (quoted) {
            throw new IOException(file + ": a quoted field is not closed");
        }
        if (any) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    /** Parser for one flat JSON object; nested values are returned as their JSON text. */
    static final class FlatJson {
        private final String s;
        private int i;

        FlatJson(String s) {
            this.s = s;
        }

        /** One object filling the whole text. */
        Map<String, String> object() {
            skipSpace();
            Map<String, String> out = objectValue();
            skipSpace();
            if (i != s.length()) {
                throw error("unexpected text after the object");
            }
            return out;
        }

        /** An array of objects filling the whole text. */
        List<Map<String, String>> array() {
            List<Map<String, String>> out = new ArrayList<>();
            skipSpace();
            if (i >= s.length() || s.charAt(i) != '[') {
                throw error("a .json dataset must be an array of objects, starting with '['");
            }
            i++;
            skipSpace();
            if (peek() == ']') {
                i++;
            } else {
                while (true) {
                    skipSpace();
                    if (peek() != '{') {
                        throw error("each element of the array must be an object");
                    }
                    out.add(objectValue());
                    skipSpace();
                    char c = next();
                    if (c == ']') {
                        break;
                    }
                    if (c != ',') {
                        throw error("expected ',' or ']'");
                    }
                }
            }
            skipSpace();
            if (i != s.length()) {
                throw error("unexpected text after the array");
            }
            return out;
        }

        private Map<String, String> objectValue() {
            Map<String, String> out = new LinkedHashMap<>();
            expect('{');
            skipSpace();
            if (peek() == '}') {
                i++;
            } else {
                while (true) {
                    skipSpace();
                    String key = string();
                    skipSpace();
                    expect(':');
                    skipSpace();
                    out.put(key, value());
                    skipSpace();
                    char c = next();
                    if (c == '}') {
                        break;
                    }
                    if (c != ',') {
                        throw error("expected ',' or '}'");
                    }
                }
            }
            return out;
        }

        private String value() {
            char c = peek();
            if (c == '"') {
                return string();
            }
            if (c == '{' || c == '[') {
                int start = i;
                skipNested();
                return s.substring(start, i);
            }
            int start = i;
            while (i < s.length() && ",}] \t".indexOf(s.charAt(i)) < 0) {
                i++;
            }
            String literal = s.substring(start, i);
            if (literal.equals("null")) {
                return null;
            }
            if (literal.equals("true") || literal.equals("false") || literal.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) {
                return literal;
            }
            throw error("unexpected value '" + literal + "'");
        }

        private void skipNested() {
            int depth = 0;
            do {
                char c = next();
                if (c == '"') {
                    i--;
                    string();
                } else if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    depth--;
                }
            } while (depth > 0);
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw error("short \\u escape");
                        }
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw error("bad escape \\" + e);
                }
            }
        }

        private void skipSpace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private char peek() {
            if (i >= s.length()) {
                throw error("unexpected end of line");
            }
            return s.charAt(i);
        }

        private char next() {
            char c = peek();
            i++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw error("expected '" + c + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            int line = 1;
            int column = 1;
            for (int k = 0; k < Math.min(i, s.length()); k++) {
                if (s.charAt(k) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new IllegalArgumentException(message + (line > 1 ? " at line " + line + ", column " : " at column ") + column);
        }
    }
}
