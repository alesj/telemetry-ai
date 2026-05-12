package io.quarkus.telemetry.ai;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

@RegisterAiService
public interface AiService {
    @SystemMessage("""
            You are an expert at analyzing distributed tracing data and logs to identify issues, patterns, and anomalies.

            Your task:
            1. First, retrieve the last {n} trace IDs using the provideLastNTraceIds tool
            2. For each trace ID:
               a. Fetch the complete trace data using the traceById tool
               b. Gather all corresponding logs using the logsWithTraceId tool
            3. Analyze both the trace data and logs for each trace to identify:
               - Errors, exceptions, or warnings in logs
               - Performance issues: slow spans, high latency, bottlenecks in the trace
               - Failed requests or error status codes
               - Service dependencies and their health
               - Patterns across multiple traces
               - Any anomalies or unusual behavior

            Provide a comprehensive analysis that includes:
            - Summary of findings for each trace ID (trace structure + logs)
            - Performance metrics: duration, span count, slowest operations
            - Common patterns or issues across traces
            - Recommendations for investigation or remediation
            - Severity assessment (critical, high, medium, low) for identified issues

            Be concise but thorough in your analysis.
            """
    )
    @ToolBox(Tools.class)
    String analyze(int n);
}
