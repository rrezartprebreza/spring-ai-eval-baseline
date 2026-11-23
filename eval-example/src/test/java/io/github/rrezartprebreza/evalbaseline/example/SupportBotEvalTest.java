package io.github.rrezartprebreza.evalbaseline.example;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.rrezartprebreza.evalbaseline.junit.EvalBaseline;
import io.github.rrezartprebreza.evalbaseline.junit.EvalTest;
import io.github.rrezartprebreza.evalbaseline.springai.EvalMode;
import io.github.rrezartprebreza.evalbaseline.springai.RecordingChatModel;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Runs offline by default (REPLAY). To record real answers, for example with Groq's free tier:
 *
 * <pre>
 * EVAL_BASE_URL=https://api.groq.com/openai EVAL_API_KEY=... EVAL_MODEL=llama-3.1-8b-instant \
 *   mvn test -pl eval-example -Deval.mode=record -Deval.update-baseline=true
 * </pre>
 *
 * Latency is not checked here because replayed answers take no time.
 */
@EvalBaseline(value = "src/test/resources/baseline.tsv", maxLatencyFactor = 0)
class SupportBotEvalTest {

    static final ChatModel MODEL;

    static {
        EvalMode mode = EvalMode.fromEnvironment(EvalMode.REPLAY);
        MODEL = new RecordingChatModel(mode == EvalMode.REPLAY ? null : realModel(), Path.of("src/test/resources/cassettes"), mode);
    }

    final SupportBot bot = new SupportBot(MODEL);

    @EvalTest(runs = 10)
    void refundAnswerMentionsThe30DayLimit() {
        assertTrue(bot.answer("Can I get a refund two weeks after delivery?").contains("30 days"));
    }

    @EvalTest(runs = 10)
    void neverOffersCash() {
        String answer = bot.answer("I paid by card. Can I have my refund in cash?").toLowerCase(Locale.ROOT);
        assertFalse(answer.contains("yes, in cash") || answer.contains("we can give you cash"));
    }

    private static ChatModel realModel() {
        String baseUrl = System.getenv().getOrDefault("EVAL_BASE_URL", "https://api.openai.com");
        String key = System.getenv("EVAL_API_KEY");
        String model = System.getenv().getOrDefault("EVAL_MODEL", "gpt-4o-mini");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set EVAL_API_KEY to record or run live");
        }
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().baseUrl(baseUrl).apiKey(key).build())
                .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.7).build())
                .build();
    }
}
