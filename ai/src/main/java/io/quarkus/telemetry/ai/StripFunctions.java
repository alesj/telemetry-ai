package io.quarkus.telemetry.ai;

import dev.langchain4j.service.tool.ToolExecutionResult;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StripFunctions {

    private static final Pattern ACCESS_LOG_PATTERN = Pattern.compile(
        "^'?[\\d.]+ - - \"(\\w+) ([^\"]+) HTTP/[\\d.]+\" (\\d+)"
    );

    private static final Pattern ERROR_ID_PATTERN = Pattern.compile(
        "error id: ([a-f0-9-]+)"
    );

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile(
        "trace[_\\s]?id[=:]?\\s*([a-f0-9-]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static Function<ToolExecutionResult, ToolExecutionResult> wrap(Function<String, String> fn) {
        return ter -> {
            String result = ter.resultText();
            String modified = fn.apply(result);
            return ToolExecutionResult.builder().resultText(modified).build();
        };
    }

    private static final Function<String, String> STRIP_LOGS = log -> {
        if (log == null || log.isBlank()) {
            return null;
        }

        String trimmed = log.trim();

        // Skip favicon requests entirely
        if (trimmed.contains("/favicon.ico")) {
            return null;
        }

        // Extract meaningful info from access logs
        Matcher accessLogMatcher = ACCESS_LOG_PATTERN.matcher(trimmed);
        if (accessLogMatcher.find()) {
            String method = accessLogMatcher.group(1);
            String path = accessLogMatcher.group(2);
            String status = accessLogMatcher.group(3);

            // Only keep error status codes (4xx, 5xx)
            int statusCode = Integer.parseInt(status);
            if (statusCode >= 400) {
                return String.format("%s %s -> %s", method, path, status);
            }
            return null;
        }

        // Keep error messages with error IDs
        if (trimmed.contains("failed") || ERROR_ID_PATTERN.matcher(trimmed).find()) {
            // Extract error ID if present
            Matcher errorIdMatcher = ERROR_ID_PATTERN.matcher(trimmed);
            if (errorIdMatcher.find()) {
                String errorId = errorIdMatcher.group(1);
                String message = trimmed.replaceAll("error id: [a-f0-9-]+", "").trim();
                return String.format("%s [error_id: %s]", message, errorId);
            }
            return trimmed;
        }

        // Keep trace ID references
        if (TRACE_ID_PATTERN.matcher(trimmed).find()) {
            return trimmed;
        }

        // Filter out if it's just noise
        return null;
    };

    public static Function<ToolExecutionResult, ToolExecutionResult> LOGS = wrap(STRIP_LOGS);

}
