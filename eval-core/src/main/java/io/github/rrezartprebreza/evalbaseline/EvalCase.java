package io.github.rrezartprebreza.evalbaseline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One row of an eval dataset: an id and named text values.
 *
 * @param id     the row's {@code id} column, or {@code row-N} when there is none
 * @param values every column of the row; nested JSON values are kept as JSON text
 */
public record EvalCase(String id, Map<String, String> values) {

    public EvalCase {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** The value of a column; throws when the column is missing, so typos fail loudly. */
    public String get(String name) {
        String value = values.get(name);
        if (value == null && !values.containsKey(name)) {
            throw new IllegalArgumentException("Case " + id + " has no column '" + name + "'. Columns: " + values.keySet());
        }
        return value;
    }

    public Optional<String> find(String name) {
        return Optional.ofNullable(values.get(name));
    }

    public int getInt(String name) {
        return Integer.parseInt(get(name).strip());
    }

    public double getDouble(String name) {
        return Double.parseDouble(get(name).strip());
    }

    public boolean getBoolean(String name) {
        return Boolean.parseBoolean(get(name).strip());
    }

    @Override
    public String toString() {
        return id;
    }
}
