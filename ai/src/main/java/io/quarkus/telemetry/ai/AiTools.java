package io.quarkus.telemetry.ai;

import java.util.List;

//import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;

@RegisterAiService
public interface AiTools {
    @UserMessage("""
                Call the traceql-search tool (NOT tempo_traceql-search) with argument query="{}"

                The response is JSON with a "traces" array. Each trace has a "traceID" field.

                Extract and return the first {n} traceID values as a list of strings.
            """)
    @OutputGuardrails(LogGuardrail.class)
    //@Tool("Provide last n trace ids")
    @McpToolBox({"tempo"})
    List<String> provideLastNTraceIds(int n);

    @UserMessage("""
            Call the 'get-trace' MCP tool with argument trace_id="{traceId}"

            Return the EXACT, UNMODIFIED result from the get-trace tool.
            Do NOT summarize, transform, or reformat the response.
            Do NOT create a simplified version.
            Return the raw JSON string exactly as received from the tool.
            """
    )
    @OutputGuardrails(LogGuardrail.class)
    //@Tool("Provide trace with trace id")
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

            Extract ONLY the log message text content from each log entry and return as a list of strings.
            Do NOT include timestamps, severity, or other metadata - just the message text.
            """
    )
    @OutputGuardrails(LogGuardrail.class)
    //@Tool("Provide logs with trace id")
    @McpToolBox({"grafana"})
    List<String> logsWithTraceId(String traceId);

    @UserMessage("""
      Call query_prometheus MCP tool with:
      - datasourceUid: "prometheus"
      - expr: "{__name__=~'.+', job!=\"opentelemetry-collector\"}"
      - queryType: "instant"
      - endTime: {datetime} in RFC3339 format

      This returns all application Prometheus metrics at the specified time: {datetime}
      Excludes opentelemetry-collector infrastructure metrics.

      Return the EXACT, UNMODIFIED result from the query_prometheus tool.
      Do NOT summarize, transform, or reformat the response.
      Return the raw JSON string exactly as received from the tool.
      """
    )
    @OutputGuardrails(LogGuardrail.class)
    //@Tool("Get all Prometheus metrics at specific time")
    @McpToolBox({"grafana"})
    String getAllMetricsForDatetime(String datetime);
}
