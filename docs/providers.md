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

**Tips**

- Record with the temperature your application uses. Recording at temperature 0 hides the variation the pass rate is meant to measure.
- Store keys in environment variables, never in the test or the cassettes.
- In replay mode no model is needed: pass `null` as the real model.
