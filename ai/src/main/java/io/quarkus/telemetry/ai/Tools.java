package io.quarkus.telemetry.ai;

import java.util.List;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;

@RegisterAiService
public interface Tools {
    @UserMessage("""
                Call the traceql-search tool (NOT tempo_traceql-search) with argument query="{}"

                The response is JSON with a "traces" array. Each trace has a "traceID" field.

                Extract and return the first {n} traceID values as a list of strings.
            """)
    @OutputGuardrails(LogGuardrail.class)
    @Tool("Provide last n trace ids")
    @McpToolBox({"tempo"})
    List<String> provideLastNTraceIds(int n);

    @UserMessage("""
            Call the 'get-trace' MCP tool with argument trace_id="{traceId}"

            Return the complete trace data as a JSON string.
            """
    )
    @Tool("Provide trace with trace id")
    @McpToolBox({"tempo"})
    String traceById(String traceId);

    @UserMessage("""
            Query Loki logs for trace ID: {traceId}

            Current date/time: {{current_date_time}}

            Call the query_loki_logs MCP tool with these parameters:
            - datasourceUid: "loki"
            - logql: Build the LogQL query exactly as follows:
              First part: stream selector using curly braces with service_name label matching regex for any non-empty value
              Second part: single pipe character (not pipe-equals)
              Third part: trace_id equals {traceId} (this filters structured metadata, NOT log content)

              CRITICAL: Do NOT use pipe-equals (|=). Use single pipe (|) for structured metadata filtering.
              The trace_id is metadata attached to log entries, not text in the log message.

            - startRfc3339: {{current_date_time}} minus 24 hours in RFC3339 format (YYYY-MM-DDTHH:MM:SSZ)
            - endRfc3339: {{current_date_time}} in RFC3339 format (YYYY-MM-DDTHH:MM:SSZ)
            - limit: 1000

            Return only the log message text content as a list of strings.
            """
    )
    @OutputGuardrails(LogGuardrail.class)
    @Tool("Provide logs with trace id")
    @McpToolBox({"grafana"})
    List<String> logsWithTraceId(String traceId);
}
