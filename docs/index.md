---
title: Spring AI Eval Baseline
---

Regression tests for LLM features in Java that know models are not deterministic.

A normal test runs once and passes or fails. An LLM answer can pass 7 times in 10, so one run tells you very little, and a strict test either breaks the build at random or gets switched off. This library runs each test several times, reports the pass rate with a confidence interval, and fails the build only when the pass rate drops for real compared with a stored baseline. Recorded answers let CI run offline and for free.

![A green eval run turning red after a prompt change](demo.gif)

## Guides

- [Getting started](getting-started.md): install, a first test, record and replay
- [Datasets and scores](datasets-and-scores.md): cases from JSONL or CSV, 0–1 scores
- [Record and replay](record-replay.md): modes, changed prompts, token cost
- [Spring Boot](spring-boot.md): zero-setup `@SpringBootTest`
- [Providers](providers.md): OpenAI, Groq, Ollama and others
- [The statistics, in plain words](statistics.md): why 9/10 then 8/10 passes and 10/10 then 5/10 fails
- [Reference](reference.md): annotations, statuses and settings

Source and issues: [github.com/rrezartprebreza/spring-ai-eval-baseline](https://github.com/rrezartprebreza/spring-ai-eval-baseline)
