package io.github.rrezartprebreza.evalbaseline.example;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.model.ChatModel;

/** Throwaway: checks that the docs snippets compile. Not merged. */
class ProviderSnippetsCompile {
    static ChatModel anthropic() {
        return AnthropicChatModel.builder()
                .anthropicApi(AnthropicApi.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(System.getenv("ANTHROPIC_MODEL"))
                        .maxTokens(1024)
                        .temperature(0.7)
                        .build())
                .build();
    }

    static ChatModel azure() {
        return AzureOpenAiChatModel.builder()
                .openAIClientBuilder(new OpenAIClientBuilder()
                        .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                        .credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY"))))
                .defaultOptions(AzureOpenAiChatOptions.builder()
                        .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT"))
                        .temperature(0.7)
                        .build())
                .build();
    }
}
