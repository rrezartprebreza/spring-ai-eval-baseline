package io.github.rrezartprebreza.evalbaseline.springai;

import io.github.rrezartprebreza.evalbaseline.CostRecorder;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Turns the token counts of each answer into cost and adds it to the current eval run through
 * {@link CostRecorder}, so {@code @EvalBaseline(maxCostPerRun = ...)} works without manual
 * {@code addCost} calls.
 *
 * <pre>{@code
 * ChatModel model = new CostTrackingChatModel(
 *         new RecordingChatModel(real, cassettes, mode),
 *         TokenPrices.perMillion(0.15, 0.60));   // USD per million input and output tokens
 * }</pre>
 *
 * Place it around a {@link RecordingChatModel}: recordings keep the token counts, so replayed
 * runs report the same cost as the recorded ones.
 */
public class CostTrackingChatModel implements ChatModel {

    private final ChatModel delegate;
    private final TokenPrices prices;

    public CostTrackingChatModel(ChatModel delegate, TokenPrices prices) {
        this.delegate = delegate;
        this.prices = prices;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatResponse response = delegate.call(prompt);
        if (response != null && response.getMetadata() != null) {
            Usage usage = response.getMetadata().getUsage();
            if (usage != null) {
                CostRecorder.add(prices.cost(usage.getPromptTokens(), usage.getCompletionTokens()));
            }
        }
        return response;
    }
}
