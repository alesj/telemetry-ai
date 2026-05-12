package io.quarkus.telemetry.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.service.tool.ToolExecutionResult;

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

}
