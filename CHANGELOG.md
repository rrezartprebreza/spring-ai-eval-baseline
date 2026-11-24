# Changelog

## 0.2.0 (2026-11-24)

### Added
- `@EvalDataset`: run an `@EvalTest` once per row of a JSONL or CSV file; failures list the rows that failed.
- Scores: `EvalTrial.score(0..1)`; the mean is compared with the baseline by a one-sided Welch t-test (`SCORE_DROP`).
- `@EvalTest(parallel = n)` runs trials on several threads.
- `CostRecorder`, `CostTrackingChatModel` and `TokenPrices`: cost per run from token counts, in replay too.
- `EvalMode.RECORD_MISSING` (`-Deval.mode=record-missing`): record only prompts without a recording.
- A missing recording names the closest recording and the first prompt line that changed.
- New module `eval-spring-boot`: wraps `ChatModel` beans when `eval.mode` is set.
- Documentation site under `docs/`.

### Changed
- Cassettes store token counts; files from 0.1.0 still replay.
- Baseline files gain three optional score columns; five-column files still read.
- Recording calls the model outside the lock, so parallel runs record concurrently.

## 0.1.0

First release: `@EvalTest`, `@EvalBaseline`, `EvalTrial`, Wilson intervals, Fisher exact test, baseline files, Markdown reports, `RecordingChatModel`, model comparison.
