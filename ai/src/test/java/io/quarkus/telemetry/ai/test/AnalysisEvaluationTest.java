package io.quarkus.telemetry.ai.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evaluation test to measure analysis quality over time.
 *
 * This test runs the AI analysis on known test cases and checks for:
 * 1. Required findings are present
 * 2. False positives are not present
 * 3. Specific metrics are cited
 * 4. Correlations are made
 *
 * Run this test before and after making changes to track quality improvements/regressions.
 */
@Disabled("Enable when you have AiService injected and test data available")
public class AnalysisEvaluationTest {

    /**
     * Evaluation criteria for analysis quality
     */
    static class EvaluationResult {
        String testCase;
        String analysisOutput;
        int detectionScore = 0; // out of 40
        int correlationScore = 0; // out of 30
        int rootCauseScore = 0; // out of 20
        int recommendationScore = 0; // out of 10
        int bonusPenalty = 0;
        List<String> findings = new ArrayList<>();
        List<String> issues = new ArrayList<>();

        int totalScore() {
            return detectionScore + correlationScore + rootCauseScore + recommendationScore + bonusPenalty;
        }

        boolean passes() {
            return totalScore() >= 70;
        }

        @Override
        public String toString() {
            return String.format("""
                Test Case: %s
                Detection: %d/40
                Correlation: %d/30
                Root Cause: %d/20
                Recommendations: %d/10
                Bonus/Penalty: %d
                TOTAL: %d/100 (%s)

                Findings:
                %s

                Issues:
                %s
                """,
                testCase,
                detectionScore, correlationScore, rootCauseScore, recommendationScore,
                bonusPenalty, totalScore(), passes() ? "PASS" : "FAIL",
                String.join("\n", findings),
                String.join("\n", issues)
            );
        }
    }

    @Test
    void evaluateMemoryPressureAnalysis() {
        // This would call AiService.analyze() with a known trace ID
        // String analysis = aiService.analyze(1); // for trace test-oom-trace-001

        String mockAnalysis = """
            ## Trace ID: test-oom-trace-001
            **Status:** Error - HTTP 500

            ### Trace Analysis
            - Root cause: OutOfMemoryError in application service

            ### Log Insights
            - Key messages: "java.lang.OutOfMemoryError: Java heap space"

            ### Metrics Correlation
            **System state at 2026-05-18T10:00:00Z:**
            - Memory: jvm_memory_used_bytes = 720MB / jvm_memory_max_bytes = 750MB (96%)

            **Correlation with trace:**
            - JVM memory at 96% capacity when error occurred, causing OutOfMemoryError

            ### Severity
            CRITICAL - Application cannot serve requests due to memory exhaustion

            ### Recommendations
            1. Increase JVM heap size from 750MB to at least 1024MB
            2. Investigate potential memory leak using heap dump analysis
            """;

        EvaluationResult result = evaluateMemoryPressure(mockAnalysis);

        System.out.println(result);

        // Save results for tracking over time
        saveEvaluationResult(result);

        assertTrue(result.passes(), "Analysis quality should meet threshold");
        assertTrue(result.totalScore() >= 80, "Memory pressure analysis should score at least 80/100");
    }

    private EvaluationResult evaluateMemoryPressure(String analysis) {
        EvaluationResult result = new EvaluationResult();
        result.testCase = "Memory Pressure Error";
        result.analysisOutput = analysis;

        // Detection (40 points)
        if (analysis.contains("HTTP 500") || analysis.contains("500")) {
            result.detectionScore += 10;
            result.findings.add("✅ Detected HTTP 500 error");
        } else {
            result.issues.add("❌ Failed to detect HTTP 500 error");
        }

        if (analysis.toLowerCase().contains("outofmemory") || analysis.toLowerCase().contains("out of memory")) {
            result.detectionScore += 15;
            result.findings.add("✅ Detected OutOfMemoryError");
        } else {
            result.issues.add("❌ Failed to detect OutOfMemoryError");
        }

        if (analysis.contains("jvm_memory_used")) {
            result.detectionScore += 15;
            result.findings.add("✅ Cited jvm_memory_used metric");
        } else {
            result.issues.add("❌ Failed to cite memory metrics");
        }

        // Correlation (30 points)
        if (analysis.matches("(?s).*jvm_memory.*\\d+%.*") || analysis.matches("(?s).*jvm_memory.*\\d+MB.*")) {
            result.correlationScore += 15;
            result.findings.add("✅ Provided specific memory percentage or value");
        } else {
            result.issues.add("❌ Did not provide specific memory values");
        }

        if (analysis.toLowerCase().contains("when error occurred") ||
            analysis.toLowerCase().contains("at the time") ||
            analysis.toLowerCase().contains("coincides")) {
            result.correlationScore += 15;
            result.findings.add("✅ Explicitly correlated metrics with error timing");
        } else {
            result.issues.add("❌ Did not correlate metrics with error timing");
        }

        // Root Cause (20 points)
        if (analysis.toLowerCase().contains("memory") &&
            (analysis.toLowerCase().contains("exhaust") ||
             analysis.toLowerCase().contains("capacity") ||
             analysis.toLowerCase().contains("pressure"))) {
            result.rootCauseScore += 20;
            result.findings.add("✅ Correctly identified memory exhaustion as root cause");
        } else {
            result.issues.add("❌ Failed to identify memory as root cause");
        }

        // Recommendations (10 points)
        if (analysis.toLowerCase().contains("increase") && analysis.toLowerCase().contains("heap")) {
            result.recommendationScore += 5;
            result.findings.add("✅ Recommended increasing heap size");
        }

        if (analysis.toLowerCase().contains("memory leak") || analysis.toLowerCase().contains("heap dump")) {
            result.recommendationScore += 5;
            result.findings.add("✅ Recommended investigating memory leak");
        }

        // Penalties
        if (analysis.toLowerCase().contains("cpu") &&
            !analysis.toLowerCase().contains("cpu usage normal")) {
            result.bonusPenalty -= 10;
            result.issues.add("⚠️ Incorrectly blamed CPU (false positive)");
        }

        return result;
    }

    private void saveEvaluationResult(EvaluationResult result) {
        try {
            Path resultsFile = Path.of("target/evaluation-results.log");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String entry = String.format("[%s] %s: %d/100 (%s)%n",
                timestamp, result.testCase, result.totalScore(), result.passes() ? "PASS" : "FAIL");

            Files.createDirectories(resultsFile.getParent());
            Files.writeString(resultsFile, entry,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to save evaluation result: " + e.getMessage());
        }
    }

    @Test
    void evaluateThreadStarvationAnalysis() {
        // Similar structure for thread starvation test case
        // Score based on: detecting slow response, correlating worker pool metrics, etc.
    }

    @Test
    void evaluateDownstreamErrorAnalysis() {
        // Test case for downstream service errors
        // Should detect error propagation, quote relevant logs, etc.
    }

    @Test
    void evaluateNoFalsePositivesOnSuccess() {
        String successAnalysis = """
            ## Trace ID: test-success-200-004
            **Status:** Success - HTTP 200
            **Duration:** 45ms

            ### Trace Analysis
            - Root cause: N/A - successful request
            - Request completed normally with good performance

            ### Metrics Correlation
            **System state at 2026-05-18T11:00:00Z:**
            - CPU: system_cpu_usage = 5%
            - Memory: jvm_memory_used_bytes = 350MB / 750MB (47%)
            - Active requests: 2

            All metrics within normal ranges.

            ### Severity
            LOW - No issues detected
            """;

        EvaluationResult result = new EvaluationResult();
        result.testCase = "Successful Request (No False Positives)";
        result.analysisOutput = successAnalysis;

        // Should NOT claim any errors
        if (!successAnalysis.toLowerCase().contains("error") ||
            successAnalysis.toLowerCase().contains("no issue")) {
            result.detectionScore = 40;
            result.findings.add("✅ Correctly identified no errors");
        } else {
            result.detectionScore = 0;
            result.issues.add("❌ False positive: claimed error on successful trace");
        }

        // Should cite metrics
        if (successAnalysis.contains("CPU") && successAnalysis.contains("Memory")) {
            result.correlationScore = 30;
            result.findings.add("✅ Cited system metrics");
        }

        result.rootCauseScore = 20; // N/A is correct
        result.recommendationScore = 10; // No recommendations needed is correct

        System.out.println(result);
        assertTrue(result.passes(), "Should not report false positives");
    }
}
