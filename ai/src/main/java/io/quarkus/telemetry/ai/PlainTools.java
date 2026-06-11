package io.quarkus.telemetry.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PlainTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    AiTools aiTools;

    @Inject
    @Named("tempoMcpClient")
    McpClient tempoMcpClient;

    @OutputGuardrails(LogGuardrail.class)
    @ToolInputGuardrails(JsonValidator.class)
    @Tool("Provide last n trace ids")
    public List<String> provideLastNTraceIds(int n) {
        System.out.println("Getting last " + n + " trace IDs...");
        try {
            // Call traceql-search MCP tool directly
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("traceql-search")
                    .arguments("{\"query\":\"{}\"}")
                    .build();
            String result = tempoMcpClient.executeTool(request).resultText();

            // Parse and extract trace IDs
            JsonNode root = MAPPER.readTree(result);
            JsonNode traces = root.path("traces");
            List<String> traceIds = new ArrayList<>();

            if (traces.isArray()) {
                int count = 0;
                for (JsonNode trace : traces) {
                    if (count >= n) break;
                    String traceId = trace.path("traceID").asText();
                    if (!traceId.isEmpty()) {
                        traceIds.add(traceId);
                        count++;
                    }
                }
            }

            System.out.println("Got trace IDs: " + traceIds);
            return traceIds;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get trace IDs", e);
        }
    }

    @OutputGuardrails(LogGuardrail.class)
    @Tool("Provide trace with trace id")
    public String traceById(String traceId) {
        System.out.println("Getting trace for ID: " + traceId);
        try {
            // Call get-trace MCP tool directly
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("get-trace")
                    .arguments("{\"trace_id\":\"" + traceId + "\"}")
                    .build();
            String traceJson = tempoMcpClient.executeTool(request).resultText();
            System.out.println("Got trace JSON (length: " + traceJson.length() + ")");

            // Extract start time immediately and store it for later retrieval
            String startTime = StripFunctions.extractRootSpanStartTime(traceJson);
            System.out.println("Extracted start time: " + startTime);

            // Store mapping for getAllMetricsForDatetime to use
            if (startTime != null) {
                traceIdToStartTime.put(traceId, startTime);
            }

            return traceJson;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get trace", e);
        }
    }

    // Store trace ID to start time mapping
    private final Map<String, String> traceIdToStartTime = new java.util.concurrent.ConcurrentHashMap<>();

    @OutputGuardrails(LogGuardrail.class)
    @Tool("Get root span start time for trace id")
    public String getRootSpanStartTime(String traceId) {
        System.out.println("Getting start time for trace ID: " + traceId);
        String startTime = traceIdToStartTime.remove(traceId);
        if (startTime == null) {
            System.out.println("WARNING: Start time not found for trace ID " + traceId + ". Call 'Provide trace with trace id' first.");
            return null;
        }
        System.out.println("Found start time: " + startTime);
        return startTime;
    }

    @Inject
    @Named("grafanaMcpClient")
    McpClient grafanaMcpClient;

    @OutputGuardrails(LogGuardrail.class)
    @Tool("Provide logs with trace id")
    public List<String> logsWithTraceId(String traceId) {
        System.out.println("Getting logs for trace ID: " + traceId);
        // Use AiTools for this one since it needs to calculate time ranges
        List<String> result = aiTools.logsWithTraceId(traceId);
        System.out.println("Got " + result.size() + " log entries");
        return result;
    }

    @OutputGuardrails(LogGuardrail.class)
    @Tool("Get all Prometheus metrics at specific time")
    public String getAllMetricsForDatetime(String datetime) {
        System.out.println("Getting metrics for datetime: " + datetime);
        try {
            // Prometheus doesn't accept fractional seconds in RFC3339 format
            // Convert "2026-05-17T11:40:36.151109Z" to "2026-05-17T11:40:36Z"
            String prometheusTime = datetime;
            if (datetime.contains(".")) {
                int dotIndex = datetime.indexOf('.');
                int zIndex = datetime.indexOf('Z', dotIndex);
                if (zIndex > 0) {
                    prometheusTime = datetime.substring(0, dotIndex) + "Z";
                }
            }
            System.out.println("Prometheus time (stripped fractional seconds): " + prometheusTime);

            // Call query_prometheus MCP tool directly
            String args = String.format(
                "{\"datasourceUid\":\"prometheus\"," +
                "\"expr\":\"{__name__=~'.+', job!=\\\"opentelemetry-collector\\\"}\"," +
                "\"queryType\":\"instant\"," +
                "\"endTime\":\"%s\"}",
                prometheusTime
            );
            System.out.println("Calling query_prometheus with args: " + args);

            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("query_prometheus")
                    .arguments(args)
                    .build();

            System.out.println("About to call grafanaMcpClient.executeTool...");
            String result = grafanaMcpClient.executeTool(request).resultText();
            System.out.println("Got metrics JSON (length: " + result.length() + ")");
            return result;
        } catch (Throwable e) {
            System.err.println("ERROR getting metrics: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to get metrics: " + e.getMessage(), e);
        }
    }
}
