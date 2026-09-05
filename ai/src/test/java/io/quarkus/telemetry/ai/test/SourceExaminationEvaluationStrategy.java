package io.quarkus.telemetry.ai.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;

public class SourceExaminationEvaluationStrategy extends AbstractEvaluationStrategy {

    private final String analysis;

    public SourceExaminationEvaluationStrategy(ObjectMapper mapper, ChatModel judge, String analysis, String originalPrompt) {
        super(mapper, judge, originalPrompt);
        this.analysis = truncate(analysis);
    }

    @Override
    protected String buildScorerPrompt(EvaluationSample<String> sample, String sourceExamination) {
        return """
                You are evaluating a SOURCE CODE EXAMINATION report produced by an AI system.

                The AI first analyzed telemetry data and produced this analysis:
                ---BEGIN ANALYSIS---
                %s
                ---END ANALYSIS---

                Then the AI examined application source code via Dev MCP workspace tools and produced
                this source examination report:
                ---BEGIN SOURCE EXAMINATION---
                %s
                ---END SOURCE EXAMINATION---

                The AI was given this prompt:
                ---BEGIN ORIGINAL PROMPT---
                %s
                ---END ORIGINAL PROMPT---

                The source examination should:
                - Reference actual source files from the application (e.g., PokeResource.java)
                - Identify specific methods responsible for issues found in the analysis
                - Include code snippets showing the relevant lines
                - Correlate each finding to telemetry evidence from the analysis
                - Provide suggested fixes or note that behavior is intentional
                - Include an architecture overview or summary

                Evaluation criteria:
                %s

                Score from 0.0 to 1.0:
                - 1.0 = correctly identifies source code for all analysis findings with evidence
                - 0.7 = identifies main code but misses some correlations or details
                - 0.4 = partially identifies code, significant gaps in evidence linkage
                - 0.0 = fails to identify relevant source code or provides no correlation

                If the score is below 0.7, check the ORIGINAL PROMPT above and explain which
                specific instructions the AI failed to follow. Suggest concrete prompt changes
                that could improve the output.

                Respond with ONLY a JSON object (no markdown, no extra text):
                {"score": <0.0-1.0>, "passed": <true if score >= 0.7>, "explanation": "<brief explanation>", "promptSuggestion": "<specific prompt changes, or null if passed>"}
                """.formatted(analysis, truncate(sourceExamination), originalPrompt, sample.expectedOutput());
    }

    @Override
    protected String logPrefix() {
        return "SourceExaminationEval";
    }
}
