package io.quarkus.telemetry.ai.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;

abstract class AbstractEvaluationStrategy implements EvaluationStrategy<String> {

    protected static final int MAX_CONTEXT_CHARS = 30_000;
    private static final int MAX_RETRIES = 3;

    private final ObjectMapper mapper;
    protected final ChatModel judge;
    protected final String originalPrompt;

    protected AbstractEvaluationStrategy(ObjectMapper mapper, ChatModel judge, String originalPrompt) {
        this.mapper = mapper;
        this.judge = judge;
        this.originalPrompt = truncate(originalPrompt);
    }

    protected abstract String buildScorerPrompt(EvaluationSample<String> sample, String output);

    protected abstract String logPrefix();

    @Override
    public EvaluationResult evaluate(EvaluationSample<String> sample, String output) {
        String prompt = buildScorerPrompt(sample, output);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String response = judge.chat(prompt);
                return parseJudgeResponse(response);
            } catch (Exception e) {
                if (attempt < MAX_RETRIES && isRateLimitError(e)) {
                    long backoff = attempt * 5_000L;
                    System.out.println("[" + logPrefix() + "] Rate limited, retrying in " + backoff + "ms...");
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    throw e;
                }
            }
        }
        return EvaluationResult.failed("Exhausted retries");
    }

    protected static String truncate(String text) {
        if (text == null) return "";
        if (text.length() <= MAX_CONTEXT_CHARS) return text;
        return text.substring(0, MAX_CONTEXT_CHARS) + "\n... [truncated, " + text.length() + " total chars]";
    }

    private static boolean isRateLimitError(Throwable e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("rate_limit")) return true;
        return e.getCause() != null && isRateLimitError(e.getCause());
    }

    private EvaluationResult parseJudgeResponse(String response) {
        try {
            String json = response.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").strip();
            }
            JsonNode node = mapper.readTree(json);
            double score = node.path("score").asDouble(0.0);
            boolean passed = node.path("passed").asBoolean(false);
            String explanation = node.path("explanation").asText("No explanation provided");

            String suggestion = node.path("promptSuggestion").asText(null);
            if (suggestion != null && !suggestion.equals("null")) {
                System.out.println("\n=== PROMPT IMPROVEMENT SUGGESTION ===");
                System.out.println(suggestion);
                System.out.println("=== END SUGGESTION ===\n");
            }

            return passed ? EvaluationResult.passed(score).withExplanation(explanation)
                    : EvaluationResult.failed(score, explanation);
        } catch (Exception e) {
            return EvaluationResult.failed("Failed to parse judge response: " + response);
        }
    }
}
