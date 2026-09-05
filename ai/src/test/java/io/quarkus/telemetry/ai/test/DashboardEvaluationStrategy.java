package io.quarkus.telemetry.ai.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;

public class DashboardEvaluationStrategy extends AbstractEvaluationStrategy {

    private final String analysis;

    public DashboardEvaluationStrategy(ObjectMapper mapper, ChatModel judge, String analysis, String originalPrompt) {
        super(mapper, judge, originalPrompt);
        this.analysis = truncate(analysis);
    }

    @Override
    protected String buildScorerPrompt(EvaluationSample<String> sample, String dashboardJson) {
        return """
                You are evaluating a GRAFANA DASHBOARD JSON produced by an AI system.

                The AI first analyzed telemetry data and produced this analysis:
                ---BEGIN ANALYSIS---
                %s
                ---END ANALYSIS---

                Then the AI generated this Grafana dashboard JSON to visualize the findings:
                ---BEGIN DASHBOARD JSON---
                %s
                ---END DASHBOARD JSON---

                The AI was given this prompt:
                ---BEGIN ORIGINAL PROMPT---
                %s
                ---END ORIGINAL PROMPT---

                The dashboard should be a valid Grafana dashboard JSON with:
                - A top-level "title" and "panels" array
                - Panels with PromQL expressions (in "targets[].expr") matching metrics from the analysis
                - Appropriate visualization types (timeseries, gauge, stat)
                - Meaningful panel titles that relate to the analysis findings

                Evaluation criteria:
                %s

                Score from 0.0 to 1.0:
                - 1.0 = valid JSON with panels covering all key metrics from the analysis
                - 0.7 = valid JSON with panels for the main metrics, minor gaps
                - 0.4 = valid JSON but missing important metrics or poor panel design
                - 0.0 = invalid JSON, no panels, or completely unrelated to the analysis

                IMPORTANT: The dashboard is a JSON object, NOT a text report. Evaluate it as
                structured data — check for "panels", "targets", "expr" fields, not prose content.

                If the score is below 0.7, check the ORIGINAL PROMPT above and explain which
                specific instructions the AI failed to follow. Suggest concrete prompt changes
                that could improve the output.

                Respond with ONLY a JSON object (no markdown, no extra text):
                {"score": <0.0-1.0>, "passed": <true if score >= 0.7>, "explanation": "<brief explanation>", "promptSuggestion": "<specific prompt changes, or null if passed>"}
                """.formatted(analysis, truncate(dashboardJson), originalPrompt, sample.expectedOutput());
    }

    @Override
    protected String logPrefix() {
        return "DashboardEval";
    }
}
