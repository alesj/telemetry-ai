package io.quarkus.telemetry.ai.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;

class EvaluationJudge {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are an expert evaluator of distributed tracing analysis reports.

            You will receive:
            1. An ANALYSIS OUTPUT produced by an AI system
            2. A TEST SPECIFICATION describing what the analysis SHOULD contain

            Score the analysis on four dimensions, each 0-25:

            COMPLETENESS (0-25): Does the analysis identify all MUST-detect items listed
            in the test specification? Deduct points for each missing required finding.
            Award full points only if ALL required findings are present.

            ACCURACY (0-25): Are the findings factually consistent with the data presented?
            Is the root cause correctly identified? Are there false positives (claiming issues
            that the specification says MUST NOT be reported)? Each false positive costs 10 points.

            CORRELATION_QUALITY (0-25): Does the analysis demonstrate three-way correlation
            between traces, logs, and metrics? Does it build causal chains rather than
            listing isolated observations? Does it explain what metrics RULE OUT as potential causes?

            ACTIONABILITY (0-25): Are recommendations specific, targeting the root cause
            rather than symptoms? Do they include specific thresholds, values, or configuration
            parameters? Generic advice like "monitor more" scores low.

            Respond with ONLY a JSON object in this exact format (no markdown, no code fences):
            {
              "completeness": <0-25>,
              "accuracy": <0-25>,
              "correlationQuality": <0-25>,
              "actionability": <0-25>,
              "justification": "<2-3 sentence explanation of scores>"
            }
            """;

    record JudgeScore(int completeness, int accuracy, int correlationQuality, int actionability, String justification) {
        int total() {
            return completeness + accuracy + correlationQuality + actionability;
        }

        boolean passes() {
            return total() >= 70;
        }

        @Override
        public String toString() {
            return String.format(
                    "Judge: %d/100 (%s)\n  Completeness: %d/25, Accuracy: %d/25, Correlation: %d/25, Actionability: %d/25\n  %s",
                    total(), passes() ? "PASS" : "FAIL",
                    completeness, accuracy, correlationQuality, actionability,
                    justification
            );
        }
    }

    private final ChatModel model;

    EvaluationJudge(ChatModel model) {
        this.model = model;
    }

    JudgeScore evaluate(String testCaseName, String expectedFindings, String expectedSeverity, String analysisOutput) {
        String userPrompt = String.format("""
                ## TEST SPECIFICATION
                Test Case: %s
                Expected Findings:
                %s
                Expected Severity: %s

                ## ANALYSIS OUTPUT
                %s
                """, testCaseName, expectedFindings, expectedSeverity, analysisOutput);

        try {
            List<ChatMessage> messages = List.of(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage(userPrompt)
            );
            ChatResponse response = model.chat(messages);
            return parseScore(response.aiMessage().text());
        } catch (Exception e) {
            return new JudgeScore(0, 0, 0, 0, "Judge call failed: " + e.getMessage());
        }
    }

    private JudgeScore parseScore(String json) {
        try {
            String cleaned = json.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```\\w*\\n?", "").replaceAll("\\n?```$", "").strip();
            }

            JsonNode node = MAPPER.readTree(cleaned);
            return new JudgeScore(
                    node.path("completeness").asInt(0),
                    node.path("accuracy").asInt(0),
                    node.path("correlationQuality").asInt(0),
                    node.path("actionability").asInt(0),
                    node.path("justification").asText("No justification provided")
            );
        } catch (Exception e) {
            return new JudgeScore(0, 0, 0, 0, "Failed to parse judge response: " + json);
        }
    }
}
