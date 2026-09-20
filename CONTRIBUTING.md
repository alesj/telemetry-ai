# Contributing to Telemetry AI

## Prerequisites

- JDK 21+
- Docker (for LGTM stack and DB tests)
- API key for at least one LLM provider (OpenAI or Grok)

## Building

```bash
./mvnw clean verify
```

To skip companion apps (proxy, app, db):

```bash
./mvnw clean verify -DskipCompanion
```

## Running Locally

Start modules in order (proxy first to initialize shared LGTM DevServices):

```bash
./mvn.proxy.sh quarkus:dev
./mvn.app.sh quarkus:dev
./mvn.ai.sh quarkus:dev
```

## Running Integration Tests

Full suite:

```bash
./run-integration-test.sh chaos openai grok
./run-integration-test.sh db grok openai
```

Specific methods:

```bash
./run-integration-methods.sh chaos openai grok analyzeLatency analyzeDeadlock
```

All provider combinations:

```bash
./run-all-integration-tests.sh chaos
./run-all-integration-tests.sh db
```

## Pull Requests

- Run `./mvnw clean verify` before submitting
- Integration tests are run nightly in CI; manual trigger is available via GitHub Actions
- Keep PRs focused -- one logical change per PR
