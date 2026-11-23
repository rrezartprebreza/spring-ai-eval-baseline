# Spring AI Eval Baseline

Regression tests for LLM features that know models are not deterministic.

A normal test runs once and passes or fails. An LLM answer can pass 7 times in 10, so one run tells you very little, and a strict test either breaks the build at random or gets switched off. This library runs each case several times, reports the pass rate with a 95% confidence interval, and fails the build only when the pass rate drops for real compared with a stored baseline. Recorded model calls let normal CI run offline and free.

Status: early (0.1.0-SNAPSHOT). Not on Maven Central yet.

## What is in it

| Module | What it does | Needs |
| --- | --- | --- |
| `eval-core` | Repeated runs, Wilson confidence interval, one-sided Fisher exact test, baseline file, Markdown report | Java 21, nothing else |
| `eval-spring-ai` | `RecordingChatModel`: record answers once, replay them in CI | Spring AI 1.0 |

## Quick start

```java
class SupportBotEval {

    ChatModel model = new RecordingChatModel(realModel, Path.of("src/test/resources/cassettes"),
            EvalMode.fromEnvironment(EvalMode.REPLAY));

    @Test
    void refundAnswersMentionThe30DayLimit() throws Exception {
        ChatClient chat = ChatClient.create(model);

        CaseResult result = RepeatedRunner.run("refund-question", 10, () -> {
            String answer = chat.prompt("Can I get a refund after two weeks?").call().content();
            return RepeatedRunner.Trial.of(answer.contains("30 days"));
        });

        Path baselineFile = Path.of("src/test/resources/baseline.tsv");
        RegressionCheck.assertNoRegression(Baseline.read(baselineFile), List.of(result),
                RegressionCheck.Options.defaults());
    }
}
```

1. Run once with `mvn test -Deval.mode=record` and a real model. Answers go to `src/test/resources/cassettes/`.
2. Save the results as the baseline with `Baseline.of(results).write(baselineFile)` and commit both.
3. CI then runs `mvn test` in REPLAY mode: no network, no cost.
4. After a prompt or model change, record again. If the pass rate falls significantly, the test fails with a table like this:

| Case | Pass rate (95% CI) | Baseline | p | p95 ms | Status |
| --- | --- | --- | --- | --- | --- |
| refund-question | 50% (24%–76%) | 100% | 0.016 | 1840 | PASS_RATE_DROP |

## When a case fails

| Status | Rule (defaults) |
| --- | --- |
| `PASS_RATE_DROP` | Pass rate fell by at least 10 points and the one-sided Fisher exact p-value is below 0.05 |
| `SLOWER` | p95 latency is more than 1.5 times the baseline |
| `OVER_BUDGET` | Cost per run is above `maxCostPerRun` (off by default) |
| `NEW` | Case is not in the baseline yet; does not fail |

Change them with `Options.defaults().withMinDrop(0.2).withAlpha(0.01)` and so on. With 10 runs, a drop from 9/10 to 8/10 is normal noise and passes; a drop from 10/10 to 5/10 fails.

## Modes

| Mode | Calls the model | Cost | Use it for |
| --- | --- | --- | --- |
| `REPLAY` | no | free | every push and pull request |
| `RECORD` | yes, saves answers | provider price | after changing a prompt or model |
| `LIVE` | yes, saves nothing | provider price | a nightly job that catches provider-side model changes |

Set the mode with `-Deval.mode=record` or the `EVAL_MODE` environment variable. For free live runs, use a local model through Ollama or a provider's free tier.

Only answer text is recorded, not token usage. Prompts are saved in plain text, so never record personal or confidential data.

## How it compares

- [Spring AI evaluators](https://docs.spring.io/spring-ai/reference/api/testing.html) (`RelevancyEvaluator`, `FactCheckingEvaluator`) judge one answer. Use them inside a trial as the pass check.
- [Dokimos](https://github.com/dokimos-dev/dokimos) is a full eval framework for Java with datasets, judges and a dashboard. This library does one narrower job (statistics, baselines, record and replay) and can sit next to it.

## Build

```
mvn verify
```

## Licence

MIT. See [LICENSE](LICENSE).
