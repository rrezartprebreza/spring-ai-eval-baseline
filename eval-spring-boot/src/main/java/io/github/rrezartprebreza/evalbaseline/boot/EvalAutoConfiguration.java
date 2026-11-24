package io.github.rrezartprebreza.evalbaseline.boot;

import io.github.rrezartprebreza.evalbaseline.springai.CostTrackingChatModel;
import io.github.rrezartprebreza.evalbaseline.springai.EvalMode;
import io.github.rrezartprebreza.evalbaseline.springai.RecordingChatModel;
import io.github.rrezartprebreza.evalbaseline.springai.TokenPrices;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Wraps every {@link ChatModel} bean of the application in a {@link RecordingChatModel}, and in a
 * {@link CostTrackingChatModel} when token prices are set, so a {@code @SpringBootTest} can record
 * and replay the real model with no setup code.
 *
 * <p>Active only when {@code eval.mode} is set (for example in {@code src/test/resources/application.properties}
 * or with {@code -Deval.mode=replay}), so production is never affected. Properties:
 * <ul>
 *   <li>{@code eval.mode}: {@code replay}, {@code record}, {@code record-missing} or {@code live};</li>
 *   <li>{@code eval.cassette-dir}: where recordings live, default {@code src/test/resources/cassettes};</li>
 *   <li>{@code eval.price.input-per-million} and {@code eval.price.output-per-million}: token prices; cost
 *       tracking is on when either is above zero.</li>
 * </ul>
 *
 * <p>Beans are wrapped as {@code ChatModel}: inject the interface, not the provider class.
 */
@AutoConfiguration
@ConditionalOnClass(ChatModel.class)
@ConditionalOnProperty(prefix = "eval", name = "mode")
public class EvalAutoConfiguration {

    @Bean
    static BeanPostProcessor evalChatModelWrapper(Environment environment) {
        return new ChatModelWrapper(environment);
    }

    /** Wraps ChatModel beans once they are initialised. */
    static final class ChatModelWrapper implements BeanPostProcessor {

        private final Environment environment;

        ChatModelWrapper(Environment environment) {
            this.environment = environment;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof ChatModel model) || bean instanceof RecordingChatModel
                    || bean instanceof CostTrackingChatModel) {
                return bean;
            }
            EvalMode mode = parseMode(environment.getRequiredProperty("eval.mode"));
            Path folder = Path.of(environment.getProperty("eval.cassette-dir", "src/test/resources/cassettes"));
            ChatModel wrapped = new RecordingChatModel(model, folder, mode);
            double input = environment.getProperty("eval.price.input-per-million", Double.class, 0.0);
            double output = environment.getProperty("eval.price.output-per-million", Double.class, 0.0);
            if (input > 0 || output > 0) {
                wrapped = new CostTrackingChatModel(wrapped, TokenPrices.perMillion(input, output));
            }
            return wrapped;
        }

        static EvalMode parseMode(String value) {
            try {
                return EvalMode.valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("eval.mode must be replay, record, record-missing or live, not '"
                        + value + "'", e);
            }
        }
    }
}
