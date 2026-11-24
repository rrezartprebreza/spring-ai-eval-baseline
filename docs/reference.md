---
title: Reference
---

[Home](index.md)

## Annotations

| Annotation | Where | Settings |
| --- | --- | --- |
| `@EvalTest` | method | `runs` (10), `caseId` (method name), `parallel` (1, 0.2.0) |
| `@EvalDataset` | method, 0.2.0 | `value` (file), `repeat` (1) |
| `@EvalBaseline` | class | `value` (file), `alpha` (0.05), `minDrop` (0.10), `maxLatencyFactor` (1.5), `maxCostPerRun` (0, off) |

Method parameters: `EvalTrial` (run index, `score`, `addCost`, `check`, `fail`) and `EvalCase` (the dataset row).

## Statuses

| Status | Fails | Meaning |
| --- | --- | --- |
| `OK` | no | within limits |
| `NEW` | no | not in the baseline yet |
| `PASS_RATE_DROP` | yes | pass rate fell significantly |
| `SCORE_DROP` | yes | mean score fell significantly (0.2.0) |
| `SLOWER` | yes | p95 latency above the limit |
| `OVER_BUDGET` | yes | cost per run above the limit |

A test where every run throws something other than an assertion error fails with that error.

## Settings

System properties, or environment variables in capitals with underscores (`EVAL_MODE`):

| Property | Default | Meaning |
| --- | --- | --- |
| `eval.mode` | – | `replay`, `record`, `record-missing`, `live` |
| `eval.update-baseline` | `false` | save results as the new baseline instead of failing |
| `eval.baseline-dir` | `src/test/resources/eval-baselines` | baseline folder when `@EvalBaseline` gives no file |
| `eval.report-dir` | `target/eval-reports` | Markdown reports |
| `eval.cassette-dir` | `src/test/resources/cassettes` | Spring Boot module only |
| `eval.price.input-per-million`, `eval.price.output-per-million` | 0 | Spring Boot module only; turns on cost tracking |

## Baseline file

Tab-separated, committed with the code:

```
caseId	runs	passes	p95Millis	totalCost
refundAnswerMentionsThe30DayLimit	10	9	-1	0.0
```

From 0.2.0, files with scores have three more columns: `scoreCount`, `scoreSum`, `scoreSumSq`. Five-column files stay valid.
