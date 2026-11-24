# Contributing

Thank you for helping. Small pull requests with a test are the easiest to review.

## Build and test

You need Java 21 and Maven 3.9.

```bash
mvn verify                     # all modules, all tests, offline (eval tests replay recordings)
mvn -pl eval-core test         # one module
```

No API key is needed: the example's eval tests replay recorded answers.

## Layout

| Module | Contents |
| --- | --- |
| `eval-core` | Statistics, baseline files, datasets, reports. Plain Java, no dependencies. |
| `eval-junit` | `@EvalTest`, `@EvalDataset`, `@EvalBaseline`, the JUnit extension. |
| `eval-spring-ai` | `RecordingChatModel`, `CostTrackingChatModel`. |
| `eval-spring-boot` | Autoconfiguration. |
| `eval-example` | A runnable example; not published. |

## Guidelines

- Add a test for every change. Statistical code needs a test against a value computed by hand or by a reference tool.
- Keep `eval-core` free of dependencies.
- Keep public APIs backward compatible: old baseline files and recordings must keep working.
- Write documentation in plain words; update `docs/` and the README when behaviour changes.
- Never commit recordings that contain personal or confidential data.

## Where to start

Issues labelled [good first issue](https://github.com/rrezartprebreza/spring-ai-eval-baseline/labels/good%20first%20issue) describe what to change and how to check it. Comment on an issue before starting so two people don't work on the same thing.
