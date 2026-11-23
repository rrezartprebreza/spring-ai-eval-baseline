package io.github.rrezartprebreza.evalbaseline.springai;

import java.util.Locale;

/** How {@link RecordingChatModel} talks to the model. */
public enum EvalMode {
    /** Call the real model and save each answer to the cassette folder. */
    RECORD,
    /** Never call the model; answer from the cassette folder. Free and offline. */
    REPLAY,
    /** Call the real model and save nothing. */
    LIVE;

    /**
     * Reads the mode from the {@code eval.mode} system property or the {@code EVAL_MODE}
     * environment variable, and falls back to {@code fallback}.
     */
    public static EvalMode fromEnvironment(EvalMode fallback) {
        String value = System.getProperty("eval.mode");
        if (value == null || value.isBlank()) {
            value = System.getenv("EVAL_MODE");
        }
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return EvalMode.valueOf(value.strip().toUpperCase(Locale.ROOT));
    }
}
