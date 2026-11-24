---
title: Record and replay
---

[Home](index.md)

`RecordingChatModel` wraps any Spring AI `ChatModel`. Each distinct prompt gets one JSON file in the cassette folder with every answer recorded for it, in order, so a model that answered correctly 9 times in 10 still does in replay.

## Modes

| Mode | Calls the model | Use it for |
| --- | --- | --- |
| `replay` | no | every push and pull request |
| `record` | yes, saves every answer | after changing a prompt or model |
| `record-missing` (0.2.0) | only for prompts without a recording | after changing one prompt |
| `live` | yes, saves nothing | a nightly job that catches provider-side model changes |

Set the mode with `-Deval.mode=...` or the `EVAL_MODE` environment variable, and read it with `EvalMode.fromEnvironment(EvalMode.REPLAY)`.

## When a prompt changed (0.2.0)

Replaying a prompt with no recording fails with a message that points at the change:

```
No recording 5db7b183edefb602.json for this prompt in src/test/resources/cassettes.
The closest recording, 71d4d1045a4d5561.json, differs from line 1 of the prompt:
  recorded: SYSTEM: Answer in two sentences.
  now:      SYSTEM: Answer in three sentences.
If the prompt change is intended, run with -Deval.mode=record-missing ...
```

`record-missing` records only the prompts that changed and replays the rest.

## Cost from token counts (0.2.0)

Recordings keep each answer's token counts. Wrap the recorder in a `CostTrackingChatModel` and every run's cost is computed from them, in replay too:

```java
ChatModel model = new CostTrackingChatModel(
        new RecordingChatModel(real, cassettes, mode),
        TokenPrices.perMillion(0.15, 0.60));
```

With `@EvalBaseline(maxCostPerRun = 0.002)` a test fails with `OVER_BUDGET` when a prompt change makes runs too expensive. Code that knows its own cost can call `CostRecorder.add(amount)` or `trial.addCost(amount)`.

## Parallel runs (0.2.0)

`@EvalTest(runs = 20, parallel = 4)` runs four trials at a time. Recording calls the model outside its lock, so parallel recording is fast too. The code under test must be thread-safe.

## Privacy

Prompts are stored in plain text. Never record personal or confidential data; use synthetic cases.
