package io.github.rrezartprebreza.evalbaseline.springai;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.stream.Stream;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
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
 * <p>The answer text and its token counts are kept, so a {@link CostTrackingChatModel} placed
 * around this one reports the same cost in replay as in the recorded run. Other response
 * metadata is not recorded. Prompts are stored in plain text: do not record personal or
 * confidential data.
 *
 * <p>When a prompt has no recording, the error names the closest recording and the first line
 * that differs, which is usually the prompt change that caused it. Run with
 * {@link EvalMode#RECORD_MISSING} to record only the missing prompts.
 */
public class RecordingChatModel implements ChatModel {

    /** Token counts of one recorded answer; either may be null when the provider did not report it. */
    public record TokenUsage(Integer promptTokens, Integer completionTokens) {
    }

    /** File format of one recording. {@code usages} is absent in files written before 0.2.0. */
    public record Cassette(String prompt, List<String> responses, List<TokenUsage> usages) {
        public Cassette(String prompt, List<String> responses) {
            this(prompt, responses, null);
        }
    }

    private final ChatModel delegate;
    private final Path folder;
    private final EvalMode mode;
    private final ObjectMapper json = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
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
    public ChatResponse call(Prompt prompt) {
        String key = keyOf(prompt);
        return switch (mode) {
            case LIVE -> delegate.call(prompt);
            case RECORD -> record(prompt, key);
            case REPLAY -> replay(key);
            case RECORD_MISSING -> {
                boolean recordedThisRun;
                synchronized (this) {
                    recordedThisRun = startedRecording.containsKey(key);
                }
                yield !recordedThisRun && Files.exists(fileFor(key)) ? replay(key) : record(prompt, key);
            }
        };
    }

    private ChatResponse record(Prompt prompt, String key) {
        // The model call happens outside the lock, so parallel runs can record at the same time.
        ChatResponse response = delegate.call(prompt);
        String text = response.getResult() == null || response.getResult().getOutput() == null
                ? "" : String.valueOf(response.getResult().getOutput().getText());
        TokenUsage usage = usageOf(response);
        Path file = fileFor(key);
        synchronized (this) {
            // The first call for a prompt in this run starts a fresh recording, so old answers do not pile up.
            List<String> answers = new ArrayList<>();
            List<TokenUsage> usages = new ArrayList<>();
            if (startedRecording.containsKey(key)) {
                Cassette old = read(file);
                answers.addAll(old.responses());
                if (old.usages() != null) {
                    usages.addAll(old.usages());
                }
            }
            answers.add(text);
            usages.add(usage);
            startedRecording.put(key, true);
            write(file, new Cassette(key, answers, usages));
        }
        return response;
    }

    private synchronized ChatResponse replay(String key) {
        Path file = fileFor(key);
        if (!Files.exists(file)) {
            throw new IllegalStateException(missingMessage(key, file));
        }
        Cassette cassette = read(file);
        List<String> answers = cassette.responses();
        if (answers == null || answers.isEmpty()) {
            throw new IllegalStateException("Recording " + file + " holds no answers");
        }
        int i = (replayPosition.merge(key, 1, Integer::sum) - 1) % answers.size();
        Generation generation = new Generation(new AssistantMessage(answers.get(i)));
        List<TokenUsage> usages = cassette.usages();
        if (usages != null && i < usages.size() && usages.get(i) != null) {
            TokenUsage u = usages.get(i);
            ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                    .usage(new DefaultUsage(u.promptTokens(), u.completionTokens()))
                    .build();
            return new ChatResponse(List.of(generation), metadata);
        }
        return new ChatResponse(List.of(generation));
    }

    private static TokenUsage usageOf(ChatResponse response) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return null;
        }
        var usage = response.getMetadata().getUsage();
        if (usage.getPromptTokens() == null && usage.getCompletionTokens() == null) {
            return null;
        }
        return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens());
    }

    /** Explains a missing recording, pointing at the closest one when the prompt only changed a little. */
    String missingMessage(String key, Path file) {
        StringBuilder sb = new StringBuilder("No recording ").append(file.getFileName()).append(" for this prompt in ")
                .append(folder).append('.');
        Cassette closest = null;
        Path closestFile = null;
        int bestShared = -1;
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json")).sorted()::iterator) {
                    Cassette c;
                    try {
                        c = read(p);
                    } catch (UncheckedIOException e) {
                        continue;
                    }
                    if (c.prompt() == null) {
                        continue;
                    }
                    int shared = sharedPrefix(c.prompt(), key);
                    if (shared > bestShared) {
                        bestShared = shared;
                        closest = c;
                        closestFile = p;
                    }
                }
            } catch (IOException e) {
                // fall through to the plain message
            }
        }
        if (closest != null && bestShared > 0) {
            String[] was = closest.prompt().split("\n", -1);
            String[] now = key.split("\n", -1);
            int line = 0;
            while (line < was.length && line < now.length && was[line].equals(now[line])) {
                line++;
            }
            sb.append("\nThe closest recording, ").append(closestFile.getFileName()).append(", differs from line ")
                    .append(line + 1).append(" of the prompt:")
                    .append("\n  recorded: ").append(line < was.length ? shorten(was[line]) : "(prompt ended)")
                    .append("\n  now:      ").append(line < now.length ? shorten(now[line]) : "(prompt ended)");
        } else {
            sb.append("\nPrompt starts with: ").append(shorten(key));
        }
        sb.append("\nIf the prompt change is intended, run with -Deval.mode=record-missing to record only the missing"
                + " prompts, or -Deval.mode=record to record everything again.");
        return sb.toString();
    }

    private static int sharedPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    private static String shorten(String s) {
        return s.length() <= 120 ? s : s.substring(0, 117) + "...";
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
