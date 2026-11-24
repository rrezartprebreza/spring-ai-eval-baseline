---
title: Getting started
---

[Home](index.md)

## Install

```xml
<dependency>
    <groupId>io.github.rrezartprebreza</groupId>
    <artifactId>eval-junit</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.github.rrezartprebreza</groupId>
    <artifactId>eval-spring-ai</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
testImplementation("io.github.rrezartprebreza:eval-junit:0.1.0")
testImplementation("io.github.rrezartprebreza:eval-spring-ai:0.1.0")
```

Features marked 0.2.0 in these guides are on `main` and not yet released.

## A first eval test

```java
@EvalBaseline(value = "src/test/resources/baseline.tsv", maxLatencyFactor = 0)
class SupportBotEvalTest {

    static final ChatModel MODEL = new RecordingChatModel(realModelOrNull(),
            Path.of("src/test/resources/cassettes"), EvalMode.fromEnvironment(EvalMode.REPLAY));

    SupportBot bot = new SupportBot(MODEL);

    @EvalTest(runs = 10)
    void mentionsThe30DayLimit() {
        assertTrue(bot.answer("Can I get a refund two weeks after delivery?").contains("30 days"));
    }
}
```

Write the method like any JUnit test: a run passes when the method returns and fails on an assertion error. The method runs 10 times; the pass rate is then compared with the baseline file.

## The workflow

1. Record real answers and save the baseline: `mvn test -Deval.mode=record -Deval.update-baseline=true`
2. Commit `src/test/resources/cassettes/` and `baseline.tsv`.
3. CI runs `mvn test`, which replays the answers: no network, no cost.
4. After changing a prompt or model, record again. A real drop fails the test with a table:

| Case | Pass rate (95% CI) | Baseline | p | p95 ms | Status |
| --- | --- | --- | --- | --- | --- |
| mentionsThe30DayLimit | 50% (24%–76%) | 100% | 0.016 | – | PASS_RATE_DROP |

If the change is intended, run with `-Deval.update-baseline=true` and commit the new baseline, so reviewers see it.

In Kotlin the same test is a plain `@EvalTest(runs = 10) fun mentionsThe30DayLimit() { ... }`.
