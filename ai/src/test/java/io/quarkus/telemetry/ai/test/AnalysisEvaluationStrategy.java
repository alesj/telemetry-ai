package io.quarkus.telemetry.ai.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;

public class AnalysisEvaluationStrategy extends AbstractEvaluationStrategy {

    private final String toolOutputContext;

    public AnalysisEvaluationStrategy(ObjectMapper mapper, ChatModel judge, String toolOutputContext, String systemPrompt) {
        super(mapper, judge, systemPrompt);
        this.toolOutputContext = truncate(toolOutputContext);
    }

    @Override
    protected String buildScorerPrompt(EvaluationSample<String> sample, String analysis) {
        return """
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
                """.formatted(toolOutputContext, analysis, originalPrompt, sample.expectedOutput());
    }

    @Override
    protected String logPrefix() {
        return "AnalysisEval";
    }
}
