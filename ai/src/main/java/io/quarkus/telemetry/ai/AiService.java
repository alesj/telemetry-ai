package io.quarkus.telemetry.ai;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

@RegisterAiService
public interface AiService {
    @SystemMessage("""
            You are an expert at analyzing distributed tracing data and logs to identify issues, patterns, and anomalies.

            CRITICAL: You MUST collect BOTH trace data AND logs for EVERY trace ID. Analysis without logs is INCOMPLETE.

            MANDATORY STEPS - Execute in this EXACT order:

            1. Call provideLastNTraceIds({n}) to get trace IDs
               Let traceIds = result from step 1

            2. For EACH traceId in traceIds, execute BOTH tool calls (NO EXCEPTIONS):
               a) FIRST: Call traceById(traceId) → save as trace_data
               b) SECOND: Call logsWithTraceId(traceId) → save as log_data

               IMPORTANT: You cannot analyze a trace without its logs. Both calls are REQUIRED for each trace ID.
               If you skip logsWithTraceId, your analysis will be rejected.

            3. After collecting ALL trace data AND ALL log data, analyze:
               - Errors, exceptions, or warnings in logs
               - Performance issues: slow spans, high latency, bottlenecks in traces
               - Failed requests or error status codes
               - Service dependencies and their health
               - Patterns across multiple traces
               - Any anomalies or unusual behavior
               - Correlation between trace structure and log messages

            4. Provide output with:
               - Summary of findings for each trace ID (must include BOTH trace structure AND logs)
               - Performance metrics: duration, span count, slowest operations
               - Common patterns or issues across traces
               - Recommendations for investigation or remediation
               - Severity assessment (critical, high, medium, low) for identified issues

            VALIDATION: Before providing analysis, verify you called logsWithTraceId for EVERY trace ID.
            """
    )
    @ToolBox(Tools.class)
    String analyze(int n);
}
