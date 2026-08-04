package io.quarkus.telemetry.ai.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;

public class AnalysisEvaluationStrategy implements EvaluationStrategy<String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CONTEXT_CHARS = 30_000;
    private static final int MAX_RETRIES = 3;

    private final ChatModel judge;
    private final String toolOutputContext;
    private final String systemPrompt;

    public AnalysisEvaluationStrategy(ChatModel judge, String toolOutputContext, String systemPrompt) {
        this.judge = judge;
        this.toolOutputContext = truncate(toolOutputContext);
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
    }

    @Override
    public EvaluationResult evaluate(EvaluationSample<String> sample, String analysis) {
        String prompt = """
                You are evaluating the quality of a telemetry analysis produced by an AI system.

                The AI received this raw telemetry data (traces, logs, metrics) as input:
                ---BEGIN TELEMETRY DATA---
                %s
                ---END TELEMETRY DATA---

                The AI produced this analysis:
                ---BEGIN ANALYSIS---
                %s
                ---END ANALYSIS---

                The AI was guided by this system prompt:
                ---BEGIN SYSTEM PROMPT---
                %s
                ---END SYSTEM PROMPT---

                Evaluation criteria:
                %s

                Score the analysis from 0.0 to 1.0 based on whether it correctly identifies \
                the issues present in the telemetry data according to the criteria above.
                - 1.0 = perfectly identifies all issues described in the criteria
                - 0.7 = identifies the main issues but misses some details
                - 0.4 = partially identifies issues, significant gaps
                - 0.0 = completely misses the issues or is irrelevant

                If score < 0.7, also suggest specific changes to the SYSTEM PROMPT that would \
                help the AI produce a better analysis. Focus on what instructions are missing or \
                too vague. Be concrete: quote the section to change and provide the improved wording.

                Respond with ONLY a JSON object (no markdown, no extra text):
                {"score": <0.0-1.0>, "passed": <true if score >= 0.7, false otherwise>, "explanation": "<brief explanation>", "promptSuggestion": "<specific system prompt changes, or null if passed>"}
                """.formatted(toolOutputContext, analysis, systemPrompt, sample.expectedOutput());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String response = judge.chat(prompt);
                return parseJudgeResponse(response);
            } catch (Exception e) {
                if (attempt < MAX_RETRIES && isRateLimitError(e)) {
                    long backoff = attempt * 5_000L;
                    System.out.println("[EvaluationStrategy] Rate limited, retrying in " + backoff + "ms...");
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    throw e;
                }
            }
        }
        return EvaluationResult.failed("Exhausted retries");
    }

    private static boolean isRateLimitError(Throwable e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("rate_limit")) return true;
        return e.getCause() != null && isRateLimitError(e.getCause());
    }

    private static String truncate(String context) {
        if (context.length() <= MAX_CONTEXT_CHARS) return context;
        return context.substring(0, MAX_CONTEXT_CHARS) + "\n... [truncated, " + context.length() + " total chars]";
    }

    private EvaluationResult parseJudgeResponse(String response) {
        try {
            String json = response.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").strip();
            }
            JsonNode node = MAPPER.readTree(json);
            double score = node.path("score").asDouble(0.0);
            boolean passed = node.path("passed").asBoolean(false);
            String explanation = node.path("explanation").asText("No explanation provided");

            String suggestion = node.path("promptSuggestion").asText(null);
            if (suggestion != null && !suggestion.equals("null")) {
                System.out.println("\n=== PROMPT IMPROVEMENT SUGGESTION ===");
                System.out.println(suggestion);
                System.out.println("=== END SUGGESTION ===\n");
            }

            if (passed) {
                return EvaluationResult.passed(score).withExplanation(explanation);
            } else {
                return EvaluationResult.failed(score, explanation);
            }
        } catch (Exception e) {
            return EvaluationResult.failed("Failed to parse judge response: " + response);
        }
    }
}
