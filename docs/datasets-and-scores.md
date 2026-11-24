---
title: Datasets and scores
---

[Home](index.md)

## Datasets (0.2.0)

Keep cases in a file and run one test per row:

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

- **Formats:** JSONL (one object per line; `//` lines are comments) and CSV with a header row. Quoted CSV fields may hold commas, quotes (`""`) and line breaks. Nested JSON values arrive as JSON text.
- **Ids:** an `id` column names each case; otherwise cases are `row-1`, `row-2`, ...
- **Reading values:** `c.get("name")` throws on a missing column, listing the columns, so typos fail loudly. `getInt`, `getDouble`, `getBoolean` and `find` are there too.
- **Repeats:** `@EvalDataset(value = "...", repeat = 5)` runs each row five times, for models that answer differently each time.
- **Result:** the pass rate over all rows is one baseline case. On a regression, the message lists the failed rows and why.

## Scores (0.2.0)

When a check is a matter of degree, record a score between 0 and 1:

```java
@EvalTest(runs = 10)
void summaryKeepsTheFacts(EvalTrial trial) {
    trial.score(factOverlap(summarise(article), expectedFacts));
}
```

The baseline file stores the count, sum and sum of squares of the scores. A test fails with `SCORE_DROP` when the mean falls by at least `minDrop` (0.10 by default) and a one-sided Welch t-test gives p below `alpha`. A scored run still counts as passed unless it fails an assertion.

## Without JUnit

`Datasets.read(path)` returns the `EvalCase` list, and `RepeatedRunner.run(id, runs, () -> Trial.scored(0.8))` gives a `CaseResult` you can pass to `EvalGate.verify`.
