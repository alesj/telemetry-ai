# Telemetry AI - Project Overview

## What It Is

Telemetry AI is a Quarkus-based system that uses LLMs to automatically analyze distributed telemetry data (traces, logs, metrics) from instrumented applications. It connects to a Grafana LGTM stack via MCP (Model Context Protocol) clients, gathers correlated telemetry, and produces structured root-cause analysis reports.

## Architecture

```
User (browser / curl)
       |
  localhost:8080        localhost:8081        localhost:8082
       |                     |                     |
  +----v-----+         +-----v-----+         +-----v-----+
  | AI Module|         |   Proxy   | ------> |    App    |
  | analyze  |         |  forward  |  REST   |  poke/    |
  | Web UI   |         |  requests |  client |  chaos    |
  +----+-----+         +-----------+         +-----------+
       |                     |                     |
       |              OpenTelemetry spans, logs, metrics
       |                     |                     |
       v                     v                     v
  +----------+         +----------------------------------+
  | LLM      |         |  LGTM Stack (shared container)   |
  | (OpenAI, |         |  Tempo | Loki | Prometheus       |
  |  Grok,   |         +----------------------------------+
  |  Gemini, |               ^         ^         ^
  |  WatsonX)|               |         |         |
  +----------+          Tempo MCP   Grafana MCP (Loki+Prom)
                             |         |
                        MCP clients in AI module
```

**Modules:**
- **proxy** (port 8081) -- Forwards requests to app, creates distributed trace chain
- **app** (port 8082) -- Synthetic application with configurable failure modes (11 chaos types: delay, memory, cpu, leak, error, exception, threadpool, contention, gc, intermittent, deadlock)
- **ext** (port 8083/8084) -- External-dependency application: DB module with JPA/Panache entities (Person) and MySQL via Toxiproxy; Weather module with REST client calling Open-Meteo API via Toxiproxy for infrastructure-level chaos injection (latency, connection cuts, outages)
- **ai** (port 8080) -- Core analysis engine with Web UI, LLM integration, MCP telemetry access

## Current Capabilities

### Analysis Engine
- 5 tools exposed to the LLM: trace ID retrieval, trace data, logs, root span timestamps, Prometheus metrics
- Three-way triangulation (trace + log + metric correlation)
- Causal chain reasoning with root cause vs symptom distinction
- Chaos/testing log detection with per-type analysis rules
- Structured output in 4 formats: HTML, Markdown, Plain Text, AsciiDoc

### LLM Provider Support
- **OpenAI** (gpt-4o-mini) -- default, fully tested
- **Grok/xAI** (grok-3-mini) -- via OpenAI-compatible API, fully tested
- **Gemini** -- configured, not yet integration-tested
- **WatsonX** (Granite) -- configured with JSON validator guardrail for model quirks
- **Anthropic** -- API key configuration exists, not yet wired

### Web UI
- Single-page app at `http://localhost:8080`
- Configurable trace count, output format, optional source examination and dashboard generation
- Three collapsible result sections with copy-to-clipboard

### Testing
- **19 integration test scenarios** exercising the full stack (proxy -> app/ext -> telemetry -> AI analysis -> LLM-as-judge scoring):
  1. Normal traffic (healthy requests)
  2. Error traffic (HTTP 4xx/5xx patterns)
  3. Latency (Thread.sleep delays)
  4. Resource pressure (memory, CPU, leaks)
  5. Cascading failure (exception + threadpool + GC)
  6. Lock contention (synchronized lock contention)
  7. Intermittent failures (~60% random failure rate across 20 requests)
  8. Network partition (app stopped/restarted mid-test)
  9. Request flood (multiple delayed requests + error)
  10. Deadlock (thread deadlock with timeout detection)
  11. Source examination (Dev MCP source code analysis with dedicated scorer)
  12. Dashboard generation (Grafana dashboard JSON creation with dedicated scorer)
  13. DB normal traffic (database person queries, healthy state)
  14. DB slow queries (Toxiproxy 3s latency injection, JDBC span detection)
  15. DB pool exhaustion (4s latency + max-size=2 pool, concurrent requests, mixed success/failure)
  16. DB outage (Toxiproxy connection cut, failure and recovery detection)
  17. Weather normal traffic (REST client calls to Open-Meteo API, healthy state)
  18. Weather slow API (Toxiproxy 3s latency injection on external weather API)
  19. Weather API outage (Toxiproxy connection cut to weather API, failure and recovery)
- LLM-as-judge evaluation with configurable scorer model
- Test automation scripts for all AI/scorer combinations (`run-integration-test.sh <chaos|db|weather> [ai-profile] [scorer]` for full suite, `run-integration-methods.sh <chaos|db|weather> [ai-profile] [scorer] <methods...>` for specific methods; supported scorers: openai, grok, watsonx)
- DB chaos testing via Toxiproxy (latency injection, connection cuts) with JDBC telemetry for query-level span visibility

### Telemetry Data Pipeline
- Tempo MCP for trace retrieval (TraceQL with `nestedSetParent = -1` for unique trace IDs)
- Grafana MCP (stdio, `uvx mcp-grafana`) for Loki logs and Prometheus metrics
- Aggressive metrics filtering (94% reduction: 586 -> ~35 entries) to stay within LLM context limits
- Trace/log data stripping to remove SDK noise

## MVP Requirements and Milestones

### M1: Core Analysis (DONE)
- [x] Multi-module Quarkus project with proxy/app/ai architecture
- [x] MCP integration with Tempo, Loki, Prometheus
- [x] System prompt engineering for three-way telemetry correlation
- [x] Structured output with per-trace analysis sections
- [x] Web UI for interactive analysis

### M2: Multi-Provider LLM Support (DONE)
- [x] OpenAI (default)
- [x] Grok/xAI via OpenAI-compatible API
- [x] Gemini
- [x] WatsonX with JSON validation guardrails
- [x] Profile-based provider selection (Maven + Quarkus profiles)

### M3: Chaos Engineering & Evaluation (DONE)
- [x] 11 chaos types in the app module (delay, memory, cpu, leak, error, exception, threadpool, contention, gc, intermittent, deadlock)
- [x] 19 integration test scenarios with LLM-as-judge scoring (10 chaos + 2 post-analysis + 4 DB + 3 weather)
- [x] Cross-provider test matrix (openai/grok as both analyzer and scorer)
- [x] Prompt engineering for chaos log detection and severity classification
- [x] Test automation scripts

### M4: Advanced Features (DONE)
- [x] Source code examination via dev-mode MCP tools
- [x] Grafana dashboard generation from analysis findings
- [x] Multi-format output (HTML, Markdown, Plain Text, AsciiDoc)
- [x] DB module with JPA/Panache entities and Toxiproxy-based chaos injection
- [x] Weather module with REST client calling Open-Meteo API via Toxiproxy for external-dependency chaos testing

### M5: Production Readiness (IN PROGRESS)
- [x] Consistent pass rate across LLM provider combinations (all 12 scenarios pass with both openai/grok and grok/openai)
- [ ] Anthropic provider integration
- [ ] Native image compilation validation
- [x] Performance benchmarking (analysis latency, token usage)
- [ ] Documentation for deployment and operations

## Current Blockers

### 1. ~~Cascading Failure Test Flakiness with Grok Scorer~~ (RESOLVED)
**Status:** All provider combinations now pass 5/5. Resolved by rewriting cascading failure test criteria to be concrete and numbered per chaos type, and fixing `chaosException()` to throw clean `RuntimeException`.

### 2. Metrics Snapshot Timing Gap
**Impact:** Prometheus metrics are queried at the trace's root span start time, but chaos operations (GC churn, thread blocking) may happen after the snapshot window.
**Root Cause:** Point-in-time metric queries can miss transient pressure. Logs contain the evidence but metrics don't confirm it.
**Workaround:** Prompt instructs the LLM to treat chaos logs as authoritative even when metrics contradict.

### 3. Gemini and WatsonX Integration Tests Not Running
**Impact:** Only OpenAI and Grok are validated by integration tests.
**Root Cause:** No `run-integration-test.sh` support for gemini/watsonx scorer configurations; API key availability varies.
**Next Step:** Add gemini/watsonx profiles to test scripts once API access is stable.

## Release Roadmap

### Project Home
The project needs a permanent home to enable community adoption and long-term maintenance:

| Option | Approach | Pros | Cons |
|--------|----------|------|------|
| **Quarkiverse top-level** | `quarkiverse/quarkus-telemetry-ai` | Extension registry, Quarkus CI, community visibility | Governance requirements, review process |
| **Red Hat GitHub org** | `redhat/telemetry-ai` | Internal control, enterprise alignment | Narrower community reach |
| **Standalone** | `telemetry-ai/telemetry-ai` | Full autonomy | Must set up all infrastructure |

**Recommendation:** Quarkiverse top-level project -- leverages the existing Quarkus extension ecosystem, CI templates, and compatibility matrix.

### Distribution

**Maven Central artifacts:**

| Artifact | Type | Contents |
|----------|------|----------|
| `telemetry-ai-core` | **Uberjar** | AI engine, MCP clients, Web UI, system prompts -- single dependency for downstream |
| `telemetry-ai-app` | Example | Reference chaos application for testing and demos |

**Release process:**
- Semantic versioning with changelogs and migration guides
- GitHub Releases with signed artifacts
- Quarkus Platform BOM alignment for dependency management

### Documentation

**Build pipeline:**

| Component | Tool | Source | Output |
|-----------|------|--------|--------|
| User docs | Antora or MkDocs | `docs/*.md` | Static site |
| API reference | SmallRye OpenAPI | JAX-RS endpoints | OpenAPI spec |
| Slides | Marp CLI | `docs/SLIDES.md` | `slides.html` |
| Javadoc | Maven Javadoc Plugin | Source code | Javadoc site |

**Hosting options:**
- **GitHub Pages** -- zero-cost, auto-deploy from `gh-pages` branch
- **Quarkiverse docs portal** -- unified with other Quarkus extensions
- **Read the Docs** -- versioned docs with search

**Update workflow:**
- PRs touching `docs/` trigger preview builds via GitHub Actions
- Slides auto-generated on merge: `npx @marp-team/marp-cli docs/SLIDES.md -o docs/slides.html --html`
- Release tags trigger full doc site rebuild and publish

### Open Questions
1. Which Quarkiverse governance model fits best (extension vs. standalone project)?
2. Should the uberjar bundle LLM provider dependencies or keep them as optional profiles?
3. Antora vs. MkDocs -- align with existing Quarkus/Quarkiverse doc toolchain?

## Help Needed

1. **API key provisioning** for Gemini and WatsonX to enable integration testing across all providers
2. **Grafana MCP server stability** -- the `uvx mcp-grafana` stdio process occasionally hangs; need guidance on production deployment approach (embedded vs external MCP server)
3. **Evaluation scoring calibration** -- the 70/100 threshold may need per-scenario tuning, or a different evaluation strategy for complex multi-chaos scenarios
4. **Initial testers** trying out AI module / app and evaluating results or/and usefulness
