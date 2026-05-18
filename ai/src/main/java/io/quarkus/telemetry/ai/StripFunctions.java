package io.quarkus.telemetry.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.service.tool.ToolExecutionResult;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;

public class StripFunctions {

    private static Function<ToolExecutionResult, ToolExecutionResult> wrap(Function<String, String> fn) {
        return ter -> {
            String result = ter.resultText();
            String modified = fn.apply(result);
            return ToolExecutionResult.builder().resultText(modified).build();
        };
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> USELESS_TRACE_FIELDS = new HashSet<>(Arrays.asList(
        // SDK metadata
        "telemetry.sdk.language",
        "telemetry.sdk.name",
        "telemetry.sdk.version",
        "webengine.name",
        "webengine.version",
        // Local development artifacts
        "host.name",
        // Response body size
        "http.response.body.size",
        // Span kind
        "kind",
        // Metrics section
        "metrics"
    ));

    private static final Set<String> USELESS_LOG_FIELDS = new HashSet<>(Arrays.asList(
        // SDK metadata
        "telemetry_sdk_language",
        "telemetry_sdk_name",
        "telemetry_sdk_version",
        "webengine_name",
        "webengine_version",
        // Local development artifacts
        "host_name",
        // Redundant or low-value fields
        "flags",
        "log_logger_namespace",
        "observed_timestamp",
        "severity_number",
        "detected_level"
    ));

    private static final Set<String> LOCALHOST_FIELDS = new HashSet<>(Arrays.asList(
        "client.address",
        "server.address"
    ));

    private static void removeUselessFields(JsonNode node, Set<String> uselessFields) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> fieldNames = obj.fieldNames();

            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode fieldValue = obj.get(fieldName);

                // Remove useless fields
                if (uselessFields.contains(fieldName)) {
                    fieldNames.remove();
                    continue;
                }

                // Remove localhost addresses
                if (LOCALHOST_FIELDS.contains(fieldName) &&
                    fieldValue.isTextual() &&
                    (fieldValue.asText().equals("127.0.0.1") || fieldValue.asText().equals("localhost"))) {
                    fieldNames.remove();
                    continue;
                }

                // Remove empty status messages
                if (fieldName.equals("message") && fieldValue.isTextual() && fieldValue.asText().isEmpty()) {
                    obj.putNull(fieldName);
                    continue;
                }

                // Recurse into nested objects/arrays
                removeUselessFields(fieldValue, uselessFields);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                removeUselessFields(item, uselessFields);
            }
        }
    }

    private static final Function<String, String> STRIP_TRACE = trace -> {
        if (trace == null || trace.isBlank()) {
            return trace;
        }

        try {
            JsonNode root = MAPPER.readTree(trace);
            removeUselessFields(root, USELESS_TRACE_FIELDS);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            // If JSON parsing fails, return original
            return trace;
        }
    };

    public static Function<ToolExecutionResult, ToolExecutionResult> TRACE = wrap(STRIP_TRACE);

    private static final Function<String, String> STRIP_LOG_DATA = logData -> {
        if (logData == null || logData.isBlank()) {
            return logData;
        }

        try {
            JsonNode root = MAPPER.readTree(logData);
            removeUselessFields(root, USELESS_LOG_FIELDS);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            // If JSON parsing fails, return original
            return logData;
        }
    };

    public static Function<ToolExecutionResult, ToolExecutionResult> LOG_DATA = wrap(STRIP_LOG_DATA);

    private static final Set<String> USELESS_METRIC_PREFIXES = new HashSet<>(Arrays.asList(
        // Netty allocator internals - too granular
        "netty_allocator_pooled_arenas",
        "netty_allocator_pooled_threadlocal_caches",
        "netty_allocator_pooled_chunk_size",
        "netty_allocator_pooled_cache_size",
        "netty_allocator_memory_pinned",
        // Static/constant metrics
        "jvm_info",
        "target_info",
        "netty_eventexecutor_workers",
        "system_cpu_count",
        "process_files_max",
        "process_start_time",
        // Per-thread metrics (too noisy)
        "netty_eventexecutor_tasks_pending",
        // Mapped buffer metrics (usually 0)
        "jvm_buffer_count_buffers;id=mapped",
        "jvm_buffer_total_capacity_bytes;id=mapped",
        "jvm_buffer_memory_used_bytes;id=mapped",
        // Less useful JVM internals
        "jvm_classes_unloaded",
        "jvm_classes_loaded",
        "jvm_threads_started",
        "jvm_threads_states",
        "jvm_gc_live_data_size",
        "jvm_memory_usage_after_gc",
        // Monotonic counters that are less useful than rates/gauges
        "process_cpu_time",
        "worker_pool_completed_total",
        // OpenTelemetry SDK internal metrics (not application metrics)
        "otel_sdk_",
        // Connection duration metrics (less useful than active connections)
        "http_server_connections_duration",
        "http_client_connections_duration"
    ));

    private static boolean isUselessMetric(String metricName) {
        if (metricName == null) {
            return false;
        }

        for (String prefix : USELESS_METRIC_PREFIXES) {
            if (metricName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static final Function<String, String> STRIP_METRICS = metrics -> {
        if (metrics == null || metrics.isBlank()) {
            return metrics;
        }

        try {
            JsonNode root = MAPPER.readTree(metrics);
            JsonNode dataArray = root.path("data");

            if (!dataArray.isArray()) {
                return metrics;
            }

            // Filter the data array
            ObjectNode result = MAPPER.createObjectNode();
            var filteredData = MAPPER.createArrayNode();

            for (JsonNode item : dataArray) {
                JsonNode metric = item.path("metric");
                String metricName = metric.path("__name__").asText("");

                // Skip useless metrics
                if (!isUselessMetric(metricName)) {
                    filteredData.add(item);
                }
            }

            result.set("data", filteredData);
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            // If JSON parsing fails, return original
            return metrics;
        }
    };

    public static Function<ToolExecutionResult, ToolExecutionResult> METRICS = wrap(STRIP_METRICS);

    /**
     * Extracts the start date/time of the root span from trace JSON.
     * The root span is identified as the span without a parentSpanId.
     *
     * @param traceJson JSON string containing trace data
     * @return ISO-8601 formatted timestamp (e.g., "2026-05-12T10:30:49.074740Z"), or null if not found
     */
    public static String extractRootSpanStartTime(String traceJson) {
        if (traceJson == null || traceJson.isBlank()) {
            return null;
        }

        try {
            JsonNode root = MAPPER.readTree(traceJson);
            JsonNode trace = root.path("trace");

            if (trace.isMissingNode()) {
                return null;
            }

            // Iterate through all services and their scopes
            JsonNode services = trace.path("services");
            if (!services.isArray()) {
                return null;
            }

            for (JsonNode service : services) {
                JsonNode scopes = service.path("scopes");
                if (!scopes.isArray()) {
                    continue;
                }

                for (JsonNode scope : scopes) {
                    JsonNode spans = scope.path("spans");
                    if (!spans.isArray()) {
                        continue;
                    }

                    for (JsonNode span : spans) {
                        // Root span has no parentSpanId
                        if (!span.has("parentSpanId")) {
                            String startTimeNano = span.path("startTimeUnixNano").asText();
                            if (!startTimeNano.isEmpty()) {
                                // Convert nanoseconds to Instant
                                long nanos = Long.parseLong(startTimeNano);
                                long seconds = nanos / 1_000_000_000L;
                                long nanoAdjustment = nanos % 1_000_000_000L;
                                Instant instant = Instant.ofEpochSecond(seconds, nanoAdjustment);
                                return instant.toString(); // ISO-8601 format
                            }
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            // If JSON parsing fails, return null
            return null;
        }
    }

}
