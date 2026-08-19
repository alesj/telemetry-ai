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
        // ALL Netty metrics - too low level for app troubleshooting
        "netty_",
        // Static/constant metrics
        "jvm_info",
        "target_info",
        "system_cpu_count",
        "process_files_max",
        "process_start_time",
        "jvm_memory_max_bytes",
        // ALL JVM buffer metrics - too low level
        "jvm_buffer_",
        // JVM classes metrics - rarely actionable
        "jvm_classes_",
        // Most thread metrics - keep only jvm_threads_live
        "jvm_threads_started",
        "jvm_threads_states",
        "jvm_threads_peak",
        "jvm_threads_daemon",
        // GC detail metrics - keep only jvm_gc_overhead
        "jvm_gc_live_data",
        "jvm_gc_max_data",
        "jvm_memory_usage_after_gc",
        // Committed bytes when we have used bytes
        "jvm_memory_committed_bytes",
        // Monotonic counters
        "process_cpu_time",
        "worker_pool_completed_total",
        // ALL OpenTelemetry SDK internals
        "otel_sdk_",
        // Connection duration summaries (keep max only for requests)
        "http_server_connections_duration",
        "http_client_connections_duration"
    ));

    private static final Set<String> USELESS_METRIC_SUFFIXES = new HashSet<>(Arrays.asList(
        "_bucket", // Histogram buckets - creates dozens of entries
        "_created" // Metric creation timestamp - not useful
    ));

    private static boolean isUselessMetric(String metricName) {
        if (metricName == null) {
            return false;
        }

        // Check prefixes
        for (String prefix : USELESS_METRIC_PREFIXES) {
            if (metricName.startsWith(prefix)) {
                return true;
            }
        }

        // Check suffixes
        for (String suffix : USELESS_METRIC_SUFFIXES) {
            if (metricName.endsWith(suffix)) {
                return true;
            }
        }

        // Filter out histogram _sum and _count when we prefer _max
        // e.g., keep http_server_requests_max_milliseconds, drop http_server_requests_milliseconds_sum/count
        if ((metricName.endsWith("_sum") || metricName.endsWith("_count")) &&
            (metricName.contains("_milliseconds") || metricName.contains("_bytes"))) {
            // These are histogram aggregations - we prefer the _max variant
            return true;
        }

        return false;
    }

    private static boolean hasUselessLabels(JsonNode metricLabels) {
        // Filter out ALL per-region memory metrics - we only want heap/nonheap totals
        // Remove: G1 Eden Space, G1 Survivor Space, G1 Old Gen, Code Cache, Compressed Class Space, Metaspace
        if (metricLabels.has("id")) {
            // If there's an "id" label, it's a per-region breakdown - filter it out
            // The aggregate metrics don't have an "id" label
            return true;
        }

        // Filter out per-allocator-type metrics - too granular
        if (metricLabels.has("allocator_type")) {
            return true;
        }

        // Filter duplicate worker pools - keep only one
        if (metricLabels.has("pool_name")) {
            String poolName = metricLabels.path("pool_name").asText("");
            // Keep only vert.x-worker-thread, skip vert.x-internal-blocking
            if (poolName.equals("vert.x-internal-blocking")) {
                return true;
            }
        }

        // Filter metrics with "le" label (histogram buckets)
        if (metricLabels.has("le")) {
            return true; // Histogram bucket - we don't need individual buckets
        }

        return false;
    }

    private static final Function<String, String> STRIP_METRICS = metrics -> {
        if (metrics == null || metrics.isBlank()) {
            System.out.println("STRIP_METRICS: metrics is null or blank, returning as-is");
            return metrics;
        }

        System.out.println("STRIP_METRICS: Input length: " + metrics.length());

        try {
            //System.out.println("STRIP_METRICS: Parsing JSON...");
            JsonNode root = MAPPER.readTree(metrics);
            //System.out.println("STRIP_METRICS: JSON parsed successfully");

            JsonNode dataArray = root.path("data");
            //System.out.println("STRIP_METRICS: Got data array, isArray: " + dataArray.isArray());

            if (!dataArray.isArray()) {
                return metrics;
            }

            int originalCount = dataArray.size();
            //System.out.println("STRIP_METRICS: Starting with " + originalCount + " metrics");

            // Filter the data array
            ObjectNode result = MAPPER.createObjectNode();
            var filteredData = MAPPER.createArrayNode();

            int filteredByName = 0;
            int filteredByLabels = 0;

            for (JsonNode item : dataArray) {
                JsonNode metric = item.path("metric");
                String metricName = metric.path("__name__").asText("");

                // Skip useless metrics by name (prefix/suffix)
                if (isUselessMetric(metricName)) {
                    filteredByName++;
                    continue;
                }

                // Skip metrics with useless labels (per-region, per-allocator, etc.)
                if (hasUselessLabels(metric)) {
                    filteredByLabels++;
                    continue;
                }

                filteredData.add(item);
            }

            int keptCount = filteredData.size();
//            System.out.println("STRIP_METRICS: Filtered out " + filteredByName + " by name, " +
//                             filteredByLabels + " by labels. Kept " + keptCount + " metrics.");

            result.set("data", filteredData);
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            System.err.println("STRIP_METRICS ERROR: " + e.getMessage());
            e.printStackTrace();
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
