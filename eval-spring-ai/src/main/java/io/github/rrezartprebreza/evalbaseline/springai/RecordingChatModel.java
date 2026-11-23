package io.github.rrezartprebreza.evalbaseline.springai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * A {@link ChatModel} that records answers once and replays them later, so eval tests run in
 * normal CI for free and without a network.
 *
 * <p>Each distinct prompt (message types, message texts and model name) gets one JSON file in
 * the cassette folder holding every answer recorded for it. When a test runs the same prompt
 * ten times in RECORD mode, ten answers are saved; REPLAY returns them in the same order, so a
 * flaky model stays flaky in replay and the pass rate means the same thing.
 *
 * <p>Only the answer text is kept. Token usage and other response metadata are not recorded.
 * Prompts are stored in plain text: do not record personal or confidential data.
 */
public class RecordingChatModel implements ChatModel {

    /** File format of one recording. */
    public record Cassette(String prompt, List<String> responses) {
    }

    private final ChatModel delegate;
    private final Path folder;
    private final EvalMode mode;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Map<String, Integer> replayPosition = new HashMap<>();
    private final Map<String, Boolean> startedRecording = new HashMap<>();

    /**
     * @param delegate the real model; may be {@code null} in REPLAY mode
     * @param folder   where cassette files live, for example {@code src/test/resources/cassettes}
     */
    public RecordingChatModel(ChatModel delegate, Path folder, EvalMode mode) {
        if (mode != EvalMode.REPLAY && delegate == null) {
            throw new IllegalArgumentException("a real ChatModel is needed in " + mode + " mode");
        }
        this.delegate = delegate;
        this.folder = folder;
        this.mode = mode;
    }

    public EvalMode mode() {
        return mode;
    }

    @Override
    public synchronized ChatResponse call(Prompt prompt) {
        String key = keyOf(prompt);
        return switch (mode) {
            case LIVE -> delegate.call(prompt);
            case RECORD -> record(prompt, key);
            case REPLAY -> replay(key);
        };
    }

    private ChatResponse record(Prompt prompt, String key) {
        ChatResponse response = delegate.call(prompt);
        String text = response.getResult() == null || response.getResult().getOutput() == null
                ? "" : String.valueOf(response.getResult().getOutput().getText());
        Path file = fileFor(key);
        // The first call for a prompt in this run starts a fresh recording, so old answers do not pile up.
        List<String> answers = new ArrayList<>();
        if (startedRecording.containsKey(key)) {
            answers.addAll(read(file).responses());
        }
        answers.add(text);
        startedRecording.put(key, true);
        write(file, new Cassette(key, answers));
        return response;
    }

    private ChatResponse replay(String key) {
        Path file = fileFor(key);
        if (!Files.exists(file)) {
            throw new IllegalStateException("No recording " + file.getFileName() + " for this prompt in " + folder
                    + ". Run once with -Deval.mode=record. Prompt starts with: "
                    + key.substring(0, Math.min(120, key.length())));
        }
        List<String> answers = read(file).responses();
        if (answers.isEmpty()) {
            throw new IllegalStateException("Recording " + file + " holds no answers");
        }
        int i = replayPosition.merge(key, 1, Integer::sum) - 1;
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answers.get(i % answers.size())))));
    }

    static String keyOf(Prompt prompt) {
        StringBuilder sb = new StringBuilder();
        if (prompt.getOptions() != null && prompt.getOptions().getModel() != null) {
            sb.append("model=").append(prompt.getOptions().getModel()).append('\n');
        }
        for (Message m : prompt.getInstructions()) {
            sb.append(m.getMessageType()).append(": ").append(m.getText()).append('\n');
        }
        return sb.toString();
    }

    Path fileFor(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return folder.resolve(HexFormat.of().formatHex(hash, 0, 8) + ".json");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Cassette read(Path file) {
        try {
            return json.readValue(file.toFile(), Cassette.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read recording " + file, e);
        }
    }

    private void write(Path file, Cassette cassette) {
        try {
            Files.createDirectories(folder);
            json.writeValue(file.toFile(), cassette);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write recording " + file, e);
        }
    }
}
