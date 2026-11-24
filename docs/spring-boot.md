---
title: Spring Boot
---

[Home](index.md)

Add `eval-spring-boot` (0.2.0) with test scope. When the property `eval.mode` is set, every `ChatModel` bean is wrapped in a `RecordingChatModel`, and in a `CostTrackingChatModel` when token prices are set.

```properties
# src/test/resources/application.properties
eval.mode=replay
eval.cassette-dir=src/test/resources/cassettes
eval.price.input-per-million=0.15
eval.price.output-per-million=0.60
```

```java
@SpringBootTest
@EvalBaseline(value = "src/test/resources/baseline.tsv", maxLatencyFactor = 0)
class SupportBotEvalTest {

    @Autowired
    SupportBot bot;

    @EvalTest(runs = 10)
    void mentionsThe30DayLimit() {
        assertThat(bot.answer("Can I get a refund two weeks after delivery?")).contains("30 days");
    }
}
```

Record with `mvn test -Deval.mode=record -Deval.update-baseline=true`; a system property overrides the file.

**Notes**

- Nothing is wrapped without `eval.mode`, so production is unaffected even if the jar ends up on the runtime classpath.
- Inject `ChatModel` or `ChatClient`, not the provider class (`OpenAiChatModel`): the bean is replaced by a wrapper that implements `ChatModel` only.
- An unknown `eval.mode` value stops the context with a message listing the valid ones.
