---
title: The statistics, in plain words
---

[Home](index.md)

## Why not just fail on any drop?

With 10 runs, a test that is truly right 90% of the time will show 8 out of 10 about one time in five, by chance alone. Failing on that would break the build at random, and people would switch the test off.

## Pass rates: Fisher's exact test

For each case the library asks: *if the model were as good as in the baseline, how likely is a result this bad?* That probability is the p-value, from a one-sided Fisher exact test, which stays accurate for small counts like 10 runs.

A case fails only when both hold:

- the pass rate fell by at least `minDrop` (10 points by default), and
- p is below `alpha` (0.05 by default).

| Baseline | Now | p | Result |
| --- | --- | --- | --- |
| 9/10 | 8/10 | 0.50 | passes: easily chance |
| 10/10 | 5/10 | 0.016 | fails: very unlikely by chance |
| 10/10 | 8/10 | 0.24 | passes: run more times to tell |

More runs make smaller drops detectable. With 4 dataset rows, even 3/4 against 40/40 is not conclusive (p = 0.09).

## The interval

Reports show a 95% Wilson interval next to each pass rate: the range of true pass rates that fit the result. 7/10 gives about 40%–89%, which is a reminder that 10 runs measure roughly.

## Scores: Welch's t-test

For scores between 0 and 1, the library compares the mean with the baseline mean using a one-sided Welch t-test, which does not assume both sides vary equally. The same `minDrop` and `alpha` apply.

## Latency and cost

These are simple limits, not tests: `SLOWER` when the 95th percentile latency exceeds the baseline times `maxLatencyFactor`, and `OVER_BUDGET` when cost per run exceeds `maxCostPerRun`. In replay mode latency means nothing, so set `maxLatencyFactor = 0` there.
