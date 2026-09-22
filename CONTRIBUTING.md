# Contributing to Telemetry AI

## Prerequisites

- JDK 21+
- Maven
- Docker (for LGTM stack and ext tests)
- `uvx` for Grafana MCP 
- API key for at least one LLM provider (OpenAI or Grok)

## Building

```bash
./mvnw clean verify
```

To skip companion apps (proxy, app, ext):

```bash
./mvnw clean verify -DskipCompanion
```

## Running Locally

Start modules in order (proxy first to initialize shared LGTM DevServices):

```bash
./mvn.proxy.sh quarkus:dev
./mvn.app.sh quarkus:dev
./mvn.ai.sh quarkus:dev -Dapp.ports=8081,8082
```

## Running Integration Tests

Full suite:

```bash
./run-integration-test.sh chaos openai grok
./run-integration-test.sh ext grok openai
```

Specific methods:

```bash
./run-integration-methods.sh chaos openai grok analyzeLatency analyzeDeadlock
```

All provider combinations:

```bash
./run-all-integration-tests.sh chaos
./run-all-integration-tests.sh ext
```

## Pull Requests

- Run `./mvnw clean verify` before submitting
- Integration tests are run nightly in CI; manual trigger is available via GitHub Actions
- Keep PRs focused -- one logical change per PR
