---
title: Providers
---

[Home](index.md)

`RecordingChatModel` works with any Spring AI `ChatModel`. To record, build the real model the way your application does. For OpenAI-compatible services (OpenAI, Groq, Ollama's `/v1` endpoint, many others), one builder covers them:

```java
ChatModel real = OpenAiChatModel.builder()
        .openAiApi(OpenAiApi.builder().baseUrl(baseUrl).apiKey(key).build())
        .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.7).build())
        .build();
```

| Service | Base URL | Notes |
| --- | --- | --- |
| OpenAI | `https://api.openai.com` | paid per token |
| Groq | `https://api.groq.com/openai` | free tier with low tokens-per-minute limits; record slowly or use `record-missing` |
| Ollama | `http://localhost:11434` | free and local; API key can be any text |

Other providers have their own Spring AI module; two common ones follow.

## Anthropic Claude

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-anthropic</artifactId>
    <scope>test</scope>
</dependency>
```

```java
ChatModel real = AnthropicChatModel.builder()
        .anthropicApi(AnthropicApi.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
        .defaultOptions(AnthropicChatOptions.builder()
                .model(System.getenv("ANTHROPIC_MODEL"))
                .maxTokens(1024)
                .temperature(0.7)
                .build())
        .build();
```

Paid per token, with no free tier; record once and replay in CI so the cost stays small. Imports: `org.springframework.ai.anthropic.AnthropicChatModel`, `AnthropicChatOptions` and `org.springframework.ai.anthropic.api.AnthropicApi`.

## Azure OpenAI

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-azure-openai</artifactId>
    <scope>test</scope>
</dependency>
```

```java
ChatModel real = AzureOpenAiChatModel.builder()
        .openAIClientBuilder(new OpenAIClientBuilder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY"))))
        .defaultOptions(AzureOpenAiChatOptions.builder()
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT"))
                .temperature(0.7)
                .build())
        .build();
```

Billed through your Azure subscription; new Azure accounts usually get starting credit. `deploymentName` is the name you gave the deployment in Azure, not the model name. Imports: `org.springframework.ai.azure.openai.AzureOpenAiChatModel`, `AzureOpenAiChatOptions`, `com.azure.ai.openai.OpenAIClientBuilder` and `com.azure.core.credential.AzureKeyCredential`.

The versions of both modules come from the Spring AI BOM (`spring-ai-bom`).

**Tips**

- Record with the temperature your application uses. Recording at temperature 0 hides the variation the pass rate is meant to measure.
- Store keys in environment variables, never in the test or the cassettes.
- In replay mode no model is needed: pass `null` as the real model.
