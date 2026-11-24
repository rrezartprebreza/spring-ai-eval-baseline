package io.github.rrezartprebreza.evalbaseline.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rrezartprebreza.evalbaseline.springai.CostTrackingChatModel;
import io.github.rrezartprebreza.evalbaseline.springai.EvalMode;
import io.github.rrezartprebreza.evalbaseline.springai.RecordingChatModel;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class EvalAutoConfigurationTest {

    /** Stands in for the provider's model bean. */
    static class FakeModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("live answer"))));
        }
    }

    @Configuration
    static class App {
        @Bean
        ChatModel chatModel() {
            return new FakeModel();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EvalAutoConfiguration.class))
            .withUserConfiguration(App.class);

    @Test
    void leavesTheModelAloneWithoutEvalMode() {
        runner.run(context -> assertThat(context.getBean(ChatModel.class)).isInstanceOf(FakeModel.class));
    }

    @Test
    void wrapsTheModelForRecordAndReplay(@TempDir Path dir) {
        runner.withPropertyValues("eval.mode=record", "eval.cassette-dir=" + dir).run(context -> {
            ChatModel model = context.getBean(ChatModel.class);
            assertThat(model).isInstanceOf(RecordingChatModel.class);
            assertThat(((RecordingChatModel) model).mode()).isEqualTo(EvalMode.RECORD);
            assertThat(model.call(new Prompt("hi")).getResult().getOutput().getText()).isEqualTo("live answer");
        });
        runner.withPropertyValues("eval.mode=replay", "eval.cassette-dir=" + dir).run(context ->
                assertThat(context.getBean(ChatModel.class).call(new Prompt("hi")).getResult().getOutput().getText())
                        .isEqualTo("live answer"));
    }

    @Test
    void addsCostTrackingWhenPricesAreSet(@TempDir Path dir) {
        runner.withPropertyValues("eval.mode=record-missing", "eval.cassette-dir=" + dir,
                        "eval.price.input-per-million=0.15", "eval.price.output-per-million=0.6")
                .run(context -> assertThat(context.getBean(ChatModel.class)).isInstanceOf(CostTrackingChatModel.class));
    }

    @Test
    void rejectsAnUnknownMode() {
        runner.withPropertyValues("eval.mode=sometimes").run(context ->
                assertThat(context).hasFailed());
    }
}
