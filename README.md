# Spring AI Eval Baseline

Regression tests for LLM features that know models are not deterministic.

[![build](https://github.com/rrezartprebreza/spring-ai-eval-baseline/actions/workflows/build.yml/badge.svg)](https://github.com/rrezartprebreza/spring-ai-eval-baseline/actions/workflows/build.yml)

A normal test runs once and passes or fails. An LLM answer can pass 7 times in 10, so one run tells you very little, and a strict test either breaks the build at random or gets switched off. This library runs each test several times, reports the pass rate with a 95% confidence interval, and fails the build only when the pass rate drops for real compared with a stored baseline. Recorded answers let normal CI run offline and free.

![A green eval run turning red after a prompt change breaks 7 of 10 answers](docs/demo.gif)

Full documentation: **https://rrezartprebreza.github.io/spring-ai-eval-baseline/**

## Install

Version 0.1.0 is on Maven Central. Add the modules you need with test scope:

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

`eval-junit` brings `eval-core` with it. For a Spring Boot app, `eval-spring-boot` (from 0.2.0) wraps your `ChatModel` bean automatically; see [Spring Boot](#spring-boot).

Gradle (Kotlin DSL):

```kotlin
testImplementation("io.github.rrezartprebreza:eval-junit:0.1.0")
testImplementation("io.github.rrezartprebreza:eval-spring-ai:0.1.0")
```

Datasets, scores, parallel runs, token cost, `record-missing` and the Spring Boot module are in 0.2.0, which is on `main` and not yet released.

## Quick start

```java
@EvalBaseline("src/test/resources/baseline.tsv")
class SupportBotEvalTest {

    SupportBot bot = new SupportBot(new RecordingChatModel(realModel,
            Path.of("src/test/resources/cassettes"), EvalMode.fromEnvironment(EvalMode.REPLAY)));

    @EvalTest(runs = 10)
    void refundAnswerMentionsThe30DayLimit() {
        assertTrue(bot.answer("Can I get a refund two weeks after delivery?").contains("30 days"));
    }
}
```

Write the method like any JUnit test. Each run passes when it returns and fails on an assertion error. After 10 runs, the pass rate is compared with the baseline.

1. Record real answers and save the baseline:
   `mvn test -Deval.mode=record -Deval.update-baseline=true`
2. Commit `src/test/resources/cassettes/` and `baseline.tsv`.
3. CI runs `mvn test`, which replays the answers: no network, no cost.
4. After changing a prompt or model, record again. A real drop fails the test:

| Case | Pass rate (95% CI) | Baseline | p | p95 ms | Status |
| --- | --- | --- | --- | --- | --- |
| refundAnswerMentionsThe30DayLimit | 50% (24%–76%) | 100% | 0.016 | 1840 | PASS_RATE_DROP |

If the change is intended, run with `-Deval.update-baseline=true` and commit the new baseline, so the change shows up in code review.

A runnable version is in [`eval-example`](eval-example): `mvn -pl eval-example -am test`.

## Modules

| Module | What it does | Needs |
| --- | --- | --- |
| `eval-core` | Repeated runs, Wilson interval, one-sided Fisher exact test, baseline file, reports, model comparison | Java 21 only |
| `eval-junit` | `@EvalTest`, `@EvalBaseline`, `EvalTrial` | JUnit 5 |
| `eval-spring-ai` | `RecordingChatModel`: record once, replay in CI; `CostTrackingChatModel` | Spring AI 1.0 |
| `eval-spring-boot` | Autoconfiguration: wraps the app's `ChatModel` when `eval.mode` is set (0.2.0) | Spring Boot 3.4+ |
| `eval-example` | A support bot with two eval tests (not published) | – |

## When a test fails

| Status | Rule (defaults) |
| --- | --- |
| `PASS_RATE_DROP` | Pass rate fell by at least 10 points and the one-sided Fisher exact p-value is below 0.05 |
| `SCORE_DROP` | Mean score fell by at least 0.10 and a one-sided Welch t-test p-value is below 0.05 (0.2.0) |
| `SLOWER` | p95 latency is more than 1.5 times the baseline |
| `OVER_BUDGET` | Cost per run is above `maxCostPerRun` (off by default) |
| `NEW` | Case is not in the baseline yet; does not fail |

Change the thresholds on the class: `@EvalBaseline(value = "...", minDrop = 0.2, alpha = 0.01, maxCostPerRun = 0.002)`. With 10 runs, 9/10 then 8/10 is noise and passes; 10/10 then 5/10 fails. In replay mode latency means nothing, so set `maxLatencyFactor = 0` there.

Take an `EvalTrial` parameter to add cost or fail a run without throwing:

```java
@EvalTest(runs = 20)
void staysPolite(EvalTrial trial) {
    ChatResponse r = chat.prompt("...").call().chatResponse();
    trial.addCost(priceOf(r.getMetadata().getUsage()));
    trial.check(!r.getResult().getOutput().getText().contains("stupid"), "rude answer");
}
```

## Datasets (0.2.0)

Keep cases in a file instead of one method per case. The test runs once per row; the pass rate over all rows is compared with the baseline, and a failure lists the rows that failed.

```java
@EvalTest
@EvalDataset("src/test/resources/refund-questions.jsonl")
void answersRefundQuestions(EvalCase c) {
    assertThat(bot.answer(c.get("question"))).contains(c.get("mustMention"));
}
```

```json
{"id": "two-weeks", "question": "Can I get a refund two weeks after delivery?", "mustMention": "30 days"}
{"id": "cash", "question": "Can I have my refund in cash?", "mustMention": "original payment method"}
```

JSONL and CSV (with a header row) are supported; an `id` column names each case. `@EvalDataset(value = "...", repeat = 5)` runs every row five times.

## Scores (0.2.0)

Not every check is pass or fail. Record a score between 0 and 1 and the mean is compared with the baseline:

```java
@EvalTest(runs = 10)
void summaryKeepsTheFacts(EvalTrial trial) {
    trial.score(overlap(summarise(article), expectedFacts));
}
```

## Parallel runs and cost (0.2.0)

`@EvalTest(runs = 20, parallel = 4)` runs four trials at a time against a slow live model; your code must then be thread-safe.

Wrap the model in a `CostTrackingChatModel` and cost per run comes from the token counts, with no `addCost` calls. Recordings keep the token counts, so replay reports the same cost:

```java
ChatModel model = new CostTrackingChatModel(
        new RecordingChatModel(real, cassettes, EvalMode.fromEnvironment(EvalMode.REPLAY)),
        TokenPrices.perMillion(0.15, 0.60)); // per million input and output tokens
```

Then `@EvalBaseline(maxCostPerRun = 0.002)` fails a test whose prompt became too expensive.

## Spring Boot

With `eval-spring-boot` (0.2.0) on the test classpath, setting `eval.mode` wraps every `ChatModel` bean in record/replay, so a `@SpringBootTest` needs no setup code:

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
    @Autowired SupportBot bot;

    @EvalTest(runs = 10)
    void mentionsThe30DayLimit() {
        assertThat(bot.answer("Can I get a refund two weeks after delivery?")).contains("30 days");
    }
}
```

Record with `mvn test -Deval.mode=record -Deval.update-baseline=true`. Without `eval.mode` nothing is wrapped, so production is unaffected. Inject `ChatModel` (or `ChatClient`), not the provider class: the bean is replaced by a wrapper.

## Kotlin

Annotations work the same from Kotlin:

```kotlin
@EvalBaseline("src/test/resources/baseline.tsv")
class SupportBotEvalTest {
    private val bot = SupportBot(model)

    @EvalTest(runs = 10)
    fun mentionsThe30DayLimit() {
        assertTrue(bot.answer("Can I get a refund two weeks after delivery?").contains("30 days"))
    }
}
```

## Broken tests

A test where every run throws something other than an assertion error (a missing recording, a wrong setup) fails with that error, so a broken test is not mistaken for a weak model.

## Modes

| Mode | Calls the model | Cost | Use it for |
| --- | --- | --- | --- |
| `REPLAY` | no | free | every push and pull request |
| `RECORD` | yes, saves answers | provider price | after changing a prompt or model |
| `LIVE` | yes, saves nothing | provider price | a nightly job that catches provider-side model changes |
| `RECORD_MISSING` | only for prompts without a recording | low | after changing one prompt (0.2.0) |

Set it with `-Deval.mode=record` (or `record-missing`, `replay`, `live`) or `EVAL_MODE`. For free live runs, use Ollama or a provider's free tier. Recording ten runs saves ten answers, and replay returns them in order, so a flaky model stays flaky in replay and the pass rate keeps its meaning.

Answer text and token counts are recorded (token counts from 0.2.0). When a prompt has no recording, the error names the closest recording and the first line that changed. Prompts are saved in plain text: never record personal or confidential data.

## Compare models

```java
Map<String, List<CaseResult>> byModel = ModelComparison.run(
        List.of("llama-3.1-8b-instant", "llama-3.3-70b-versatile"),
        model -> List.of(RepeatedRunner.run("refund", 10, () -> trialWith(model))));
System.out.println(ModelComparison.toMarkdown(byModel));
```

This gives one row per case and one column per model, with the pass rate, its interval and the cost per run.

## Reports in pull requests

Each test class writes `target/eval-reports/<Class>.md`. The included [build workflow](.github/workflows/build.yml) adds them to the run summary and keeps one updated comment on every pull request.

## Settings

| Property (or env var) | Default | Meaning |
| --- | --- | --- |
| `eval.mode` (`EVAL_MODE`) | `replay` in the example | `record`, `replay`, `live` or `record-missing` |
| `eval.update-baseline` | `false` | Save results as the new baseline instead of failing |
| `eval.baseline-dir` | `src/test/resources/eval-baselines` | Baseline folder when `@EvalBaseline` gives no file |
| `eval.report-dir` | `target/eval-reports` | Where Markdown reports go |

## Used in

- [misconception-feedback](https://github.com/rrezartprebreza/misconception-feedback): a research tool that detects student misconceptions in data structures and algorithms code. [`DetectorEvalTest`](https://github.com/rrezartprebreza/misconception-feedback/blob/main/src/test/java/dev/rrezart/misconception/evalbaseline/DetectorEvalTest.java) runs the detector on a synthetic answer set, one answer per run, and fails CI when a prompt or catalogue change makes detection significantly worse. Answers were recorded once with a local Ollama model and are replayed in CI.

Using the library in your project? Open a pull request to add it here.

## How it compares

- [Spring AI evaluators](https://docs.spring.io/spring-ai/reference/api/testing.html) (`RelevancyEvaluator`, `FactCheckingEvaluator`) judge one answer. Use them inside an `@EvalTest` as the check.
- [Dokimos](https://github.com/dokimos-dev/dokimos) is a full eval framework for Java with datasets, judges and a dashboard. This library does one narrower job (statistics, baselines, record and replay) and can sit next to it.

## Build and release

`mvn verify` builds and runs everything offline. Releases go to Maven Central from a tag; see [RELEASING.md](RELEASING.md).

## Licence

MIT. See [LICENSE](LICENSE).
