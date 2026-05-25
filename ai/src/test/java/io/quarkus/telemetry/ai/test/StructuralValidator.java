package io.quarkus.telemetry.ai.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

class StructuralValidator {

    private static final Map<String, Pattern> REQUIRED_SECTIONS = new LinkedHashMap<>();

    static {
        REQUIRED_SECTIONS.put("Trace ID", Pattern.compile("(?m)^##\\s+Trace ID:"));
        REQUIRED_SECTIONS.put("Timestamp", Pattern.compile("\\*\\*Timestamp[:\\*]"));
        REQUIRED_SECTIONS.put("Duration", Pattern.compile("\\*\\*Duration[:\\*]"));
        REQUIRED_SECTIONS.put("Status", Pattern.compile("\\*\\*Status[:\\*]"));
        REQUIRED_SECTIONS.put("Request Details", Pattern.compile("(?m)^###\\s+Request Details"));
        REQUIRED_SECTIONS.put("Issue Summary", Pattern.compile("(?m)^###\\s+Issue Summary"));
        REQUIRED_SECTIONS.put("Trace Analysis", Pattern.compile("(?m)^###\\s+Trace Analysis"));
        REQUIRED_SECTIONS.put("Log Insights", Pattern.compile("(?m)^###\\s+Log Insights"));
        REQUIRED_SECTIONS.put("Three-Way Correlation", Pattern.compile("(?m)^###\\s+Three-Way Correlation"));
        REQUIRED_SECTIONS.put("Severity", Pattern.compile("(?m)^###\\s+Severity"));
        REQUIRED_SECTIONS.put("Recommendations", Pattern.compile("(?m)^###\\s+Recommendations"));
    }

    private static final Pattern CROSS_TRACE_SUMMARY = Pattern.compile("(?m)^##\\s+Cross-Trace Summary");
    private static final Pattern SEVERITY_VALUE = Pattern.compile("(?i)\\b(CRITICAL|HIGH|MEDIUM|LOW)\\b");
    private static final Pattern NUMBERED_RECOMMENDATIONS = Pattern.compile("(?m)^\\d+\\.");
    private static final Pattern CAUSAL_CHAIN = Pattern.compile("(?i)causal chain");
    private static final Pattern RULE_OUT = Pattern.compile("(?i)rule[s]? out");

    record Result(
            int sectionCount,
            int totalSections,
            List<String> missingSections,
            boolean hasCrossTraceSummary,
            boolean hasSeverityValue,
            boolean hasNumberedRecommendations,
            boolean hasCausalChain,
            boolean hasRuleOut
    ) {
        boolean passes() {
            return sectionCount >= 9;
        }

        int score() {
            int s = sectionCount * 2;
            if (hasCrossTraceSummary) s += 2;
            if (hasSeverityValue) s += 1;
            if (hasNumberedRecommendations) s += 1;
            if (hasCausalChain) s += 2;
            if (hasRuleOut) s += 2;
            return s;
        }

        int maxScore() {
            return (totalSections * 2) + 2 + 1 + 1 + 2 + 2;
        }

        @Override
        public String toString() {
            return String.format(
                    "Structural: %d/%d sections (%s), score %d/%d%s%s",
                    sectionCount, totalSections,
                    passes() ? "PASS" : "FAIL",
                    score(), maxScore(),
                    missingSections.isEmpty() ? "" : "\n  Missing: " + String.join(", ", missingSections),
                    (!hasCausalChain || !hasRuleOut) ?
                            "\n  Correlation gaps:" +
                                    (!hasCausalChain ? " [no causal chain]" : "") +
                                    (!hasRuleOut ? " [no rule-out]" : "")
                            : ""
            );
        }
    }

    static Result validate(String analysis) {
        List<String> missing = new ArrayList<>();
        int found = 0;

        for (Map.Entry<String, Pattern> entry : REQUIRED_SECTIONS.entrySet()) {
            if (entry.getValue().matcher(analysis).find()) {
                found++;
            } else {
                missing.add(entry.getKey());
            }
        }

        return new Result(
                found,
                REQUIRED_SECTIONS.size(),
                missing,
                CROSS_TRACE_SUMMARY.matcher(analysis).find(),
                SEVERITY_VALUE.matcher(analysis).find(),
                NUMBERED_RECOMMENDATIONS.matcher(analysis).find(),
                CAUSAL_CHAIN.matcher(analysis).find(),
                RULE_OUT.matcher(analysis).find()
        );
    }
}
