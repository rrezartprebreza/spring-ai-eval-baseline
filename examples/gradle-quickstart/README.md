# Gradle quickstart

A small support bot with two eval tests, built with Gradle against the released artifacts on Maven Central (`io.github.rrezartprebreza:eval-junit:0.2.0` and `eval-spring-ai:0.2.0`).

## Run

```bash
./gradlew test
```

The tests replay the recorded answers in `src/test/resources/cassettes`, so they need no network access to a model and no API key. Each test runs 10 times and its pass rate is compared with `src/test/resources/baseline.tsv`.

The recordings in this folder were written by hand so the example works offline (see `cassettes/README.md`).

## Record real answers

With any OpenAI-compatible service, for example a local Ollama:

```bash
EVAL_BASE_URL=http://localhost:11434 EVAL_API_KEY=ollama EVAL_MODEL=qwen2.5-coder:7b \
  ./gradlew test -Deval.mode=record -Deval.update-baseline=true
```

Then commit the new cassettes and baseline. `-Deval.mode=record-missing` records only prompts that have no recording yet.

## Use it in your project

Copy the `dependencies` block from `build.gradle.kts`, and the `tasks.test` block so `-Deval.*` settings reach the test JVM. The [documentation](https://rrezartprebreza.github.io/spring-ai-eval-baseline/) covers datasets, scores, cost and Spring Boot.
