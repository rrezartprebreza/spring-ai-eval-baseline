package io.github.rrezartprebreza.evalbaseline.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.rrezartprebreza.evalbaseline.CostRecorder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class RecordingFeaturesTest {

    /** Answers "a1", "a2", ... and reports 100 prompt and 20 completion tokens per answer. */
    static class MeteredModel implements ChatModel {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            String text = "a" + calls.incrementAndGet();
            ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(new DefaultUsage(100, 20)).build();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
        }
    }

    private static Prompt prompt(String system, String user) {
        return new Prompt(List.of(new SystemMessage(system), new UserMessage(user)));
    }

    private static String text(ChatResponse r) {
        return r.getResult().getOutput().getText();
    }

    @Test
    void recordMissingReplaysKnownPromptsAndRecordsOnlyNewOnes(@TempDir Path dir) {
        MeteredModel real = new MeteredModel();
        new RecordingChatModel(real, dir, EvalMode.RECORD).call(prompt("sys", "old question"));
        assertEquals(1, real.calls.get());

        RecordingChatModel mixed = new RecordingChatModel(real, dir, EvalMode.RECORD_MISSING);
        assertEquals("a1", text(mixed.call(prompt("sys", "old question"))));
        assertEquals(1, real.calls.get());
        assertEquals("a2", text(mixed.call(prompt("sys", "new question"))));
        assertEquals("a3", text(mixed.call(prompt("sys", "new question"))));
        assertEquals(3, real.calls.get());

        RecordingChatModel replay = new RecordingChatModel(null, dir, EvalMode.REPLAY);
        assertEquals("a2", text(replay.call(prompt("sys", "new question"))));
        assertEquals("a3", text(replay.call(prompt("sys", "new question"))));
    }

    @Test
    void missingRecordingPointsAtTheChangedLine(@TempDir Path dir) {
        new RecordingChatModel(new MeteredModel(), dir, EvalMode.RECORD)
                .call(prompt("Answer in two sentences.", "Can I get a refund?"));
        RecordingChatModel replay = new RecordingChatModel(null, dir, EvalMode.REPLAY);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> replay.call(prompt("Answer in three sentences.", "Can I get a refund?")));

        String message = e.getMessage();
        assertTrue(message.contains("differs from line 1"), message);
        assertTrue(message.contains("recorded: SYSTEM: Answer in two sentences."), message);
        assertTrue(message.contains("now:      SYSTEM: Answer in three sentences."), message);
        assertTrue(message.contains("record-missing"), message);
    }

    @Test
    void tokenCountsAreRecordedAndReplayedAsCost(@TempDir Path dir) throws Exception {
        TokenPrices prices = TokenPrices.perMillion(1.0, 4.0);
        ChatModel recording = new CostTrackingChatModel(new RecordingChatModel(new MeteredModel(), dir, EvalMode.RECORD), prices);
        CostRecorder.start();
        recording.call(prompt("s", "q"));
        double recordedCost = CostRecorder.stop();

        ChatModel replaying = new CostTrackingChatModel(new RecordingChatModel(null, dir, EvalMode.REPLAY), prices);
        CostRecorder.start();
        replaying.call(prompt("s", "q"));
        double replayedCost = CostRecorder.stop();

        assertEquals((100 * 1.0 + 20 * 4.0) / 1_000_000, recordedCost, 1e-15);
        assertEquals(recordedCost, replayedCost, 1e-15);
    }

    @Test
    void oldCassettesWithoutTokenCountsStillReplay(@TempDir Path dir) throws Exception {
        RecordingChatModel probe = new RecordingChatModel(null, dir, EvalMode.REPLAY);
        String key = RecordingChatModel.keyOf(new Prompt("hello"));
        Files.createDirectories(dir);
        Files.writeString(probe.fileFor(key), "{\"prompt\": \"" + key.replace("\n", "\\n") + "\", \"responses\": [\"hi\"]}");
        assertEquals("hi", text(probe.call(new Prompt("hello"))));
    }

    @Test
    void modeNamesAcceptDashes() {
        System.setProperty("eval.mode", "record-missing");
        try {
            assertEquals(EvalMode.RECORD_MISSING, EvalMode.fromEnvironment(EvalMode.REPLAY));
        } finally {
            System.clearProperty("eval.mode");
        }
    }
}
