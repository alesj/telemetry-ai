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
                Call traceql-search tool with argument query="{}"

                The response is JSON with a "traces" array. Each trace has a "traceID" field.

                Extract and return the first {n} traceID values as a list of strings.
            """)
    @OutputGuardrails(LogGuardrail.class)
    @Tool("Provide last n trace ids")
    @McpToolBox({"tempo"})
    List<String> provideLastNTraceIds(int n);

    @UserMessage("""
            Use the grafana MCP tool to query Loki logs for a specific trace ID:
            1. Query the Loki datasource (uid: "loki") for logs containing the trace ID
            2. Use LogQL query: {{service_name=~".+"}} |= "{traceId}" to filter logs by trace ID
            3. Set time range to last 24 hours
            4. Extract the log lines from the response
            5. Return only the actual log text content as a list of strings
            6. Skip any null or empty results

            Trace ID to search for: {traceId}
            """
    )
    @Tool("Provide logs with trace id")
    @McpToolBox({"grafana"})
    List<String> logsWithTraceId(String traceId);
}
