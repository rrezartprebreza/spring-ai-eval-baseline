# Spring AI Eval Baseline

Regression tests for LLM features that know models are not deterministic.

[![build](https://github.com/rrezartprebreza/spring-ai-eval-baseline/actions/workflows/build.yml/badge.svg)](https://github.com/rrezartprebreza/spring-ai-eval-baseline/actions/workflows/build.yml)

A normal test runs once and passes or fails. An LLM answer can pass 7 times in 10, so one run tells you very little, and a strict test either breaks the build at random or gets switched off. This library runs each test several times, reports the pass rate with a 95% confidence interval, and fails the build only when the pass rate drops for real compared with a stored baseline. Recorded answers let normal CI run offline and free.

![A green eval run turning red after a prompt change breaks 7 of 10 answers](docs/demo.gif)

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

`eval-junit` brings `eval-core` with it. Gradle: `testImplementation("io.github.rrezartprebreza:eval-junit:0.1.0")`.

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
| `eval-spring-ai` | `RecordingChatModel`: record once, replay in CI | Spring AI 1.0 |
| `eval-example` | A support bot with two eval tests (not published) | – |

## When a test fails

| Status | Rule (defaults) |
| --- | --- |
| `PASS_RATE_DROP` | Pass rate fell by at least 10 points and the one-sided Fisher exact p-value is below 0.05 |
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

A test where every run throws something other than an assertion error (a missing recording, a wrong setup) fails with that error, so a broken test is not mistaken for a weak model.

## Modes

| Mode | Calls the model | Cost | Use it for |
| --- | --- | --- | --- |
| `REPLAY` | no | free | every push and pull request |
| `RECORD` | yes, saves answers | provider price | after changing a prompt or model |
| `LIVE` | yes, saves nothing | provider price | a nightly job that catches provider-side model changes |

Set it with `-Deval.mode=record` or `EVAL_MODE`. For free live runs, use Ollama or a provider's free tier. Recording ten runs saves ten answers, and replay returns them in order, so a flaky model stays flaky in replay and the pass rate keeps its meaning.

Only answer text is recorded, not token usage. Prompts are saved in plain text: never record personal or confidential data.

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
| `eval.mode` (`EVAL_MODE`) | `replay` in the example | `record`, `replay` or `live` |
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
