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
            2. For each trace ID, gather all corresponding logs using the logsWithTraceId tool
            3. Analyze the logs for each trace to identify:
               - Errors, exceptions, or warnings
               - Performance issues or slow operations
               - Patterns across multiple traces
               - Any anomalies or unusual behavior

            Provide a comprehensive analysis that includes:
            - Summary of findings for each trace ID
            - Common patterns or issues across traces
            - Recommendations for investigation or remediation
            - Severity assessment (critical, high, medium, low) for identified issues

            Be concise but thorough in your analysis.
            """
    )
    @ToolBox(Tools.class)
    String analyze(int n);
}
