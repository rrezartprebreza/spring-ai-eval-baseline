package io.github.rrezartprebreza.evalbaseline.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class RecordingChatModelTest {

    /** A fake model that answers "a1", "a2", "a3", ... and counts its calls. */
    static class CountingModel implements ChatModel {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            String text = "a" + calls.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }
    }

    private static String text(ChatResponse r) {
        return r.getResult().getOutput().getText();
    }

    @Test
    void replaysRecordedAnswersInOrderWithoutCallingTheModel(@TempDir Path dir) {
        CountingModel real = new CountingModel();
        RecordingChatModel recorder = new RecordingChatModel(real, dir, EvalMode.RECORD);
        for (int i = 0; i < 3; i++) {
            recorder.call(new Prompt("Is 7 prime?"));
        }
        assertEquals(3, real.calls.get());

        RecordingChatModel replay = new RecordingChatModel(null, dir, EvalMode.REPLAY);
        assertEquals("a1", text(replay.call(new Prompt("Is 7 prime?"))));
        assertEquals("a2", text(replay.call(new Prompt("Is 7 prime?"))));
        assertEquals("a3", text(replay.call(new Prompt("Is 7 prime?"))));
        assertEquals("a1", text(replay.call(new Prompt("Is 7 prime?"))));
        assertEquals(3, real.calls.get());
    }

    @Test
    void newRecordingRunReplacesOldAnswers(@TempDir Path dir) {
        CountingModel real = new CountingModel();
        new RecordingChatModel(real, dir, EvalMode.RECORD).call(new Prompt("q"));
        new RecordingChatModel(real, dir, EvalMode.RECORD).call(new Prompt("q"));

        RecordingChatModel replay = new RecordingChatModel(null, dir, EvalMode.REPLAY);
        assertEquals("a2", text(replay.call(new Prompt("q"))));
        assertEquals("a2", text(replay.call(new Prompt("q"))));
    }

    @Test
    void replayWithoutRecordingExplainsWhatToDo(@TempDir Path dir) {
        RecordingChatModel replay = new RecordingChatModel(null, dir, EvalMode.REPLAY);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> replay.call(new Prompt("never asked")));
        assertEquals(true, e.getMessage().contains("-Deval.mode=record"));
    }

    @Test
    void liveModeNeedsARealModel(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class, () -> new RecordingChatModel(null, dir, EvalMode.LIVE));
    }
}
