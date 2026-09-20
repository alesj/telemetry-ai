package io.quarkiverse.telemetry.ai.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.evaluation.junit5.ScorerConfiguration;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkiverse.telemetry.ai.DevMcpAiService;
import io.quarkiverse.telemetry.ai.DevMcpToolProviderSupplier;
import io.quarkiverse.telemetry.ai.TelemetryAiService;
import io.quarkiverse.telemetry.ai.ToolOutputCapture;
import jakarta.inject.Inject;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static io.quarkiverse.telemetry.ai.DashboardUtils.sanitizeDashboardJson;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AppsTestBase {

    @Inject
    TelemetryAiService aiService;

    @Inject
    ObjectMapper mapper;

    @Inject
    ToolOutputCapture capture;

    @Inject
    @ModelName("scorer")
    ChatModel chatModel;

    @ScorerConfiguration
    Scorer scorer;

    @Inject
    DevMcpAiService devMcpAiService;

    @Inject
    DevMcpToolProviderSupplier devMcpTools;

    String waitForAnalysis(String label, int traceCount) throws Exception {
        System.out.println("[" + getClass().getSimpleName() + "] Waiting 60s for telemetry ingestion (" + label + ")...");
        TimeUnit.SECONDS.sleep(60);

        String analysis = aiService.analyze(traceCount, "markdown");

        System.out.println("\n=== " + label + " ANALYSIS OUTPUT ===");
        System.out.println(analysis);
        System.out.println("=== END " + label + " ANALYSIS OUTPUT ===\n");

        assertNotNull(analysis, label + ": analysis should not be null");
        assertFalse(analysis.isBlank(), label + ": analysis should not be blank");
        return analysis;
    }

    void waitAndAnalyze(String label, int traceCount, String criteria) throws Exception {
        System.out.println("[" + getClass().getSimpleName() + "] Waiting 60s for telemetry ingestion (" + label + ")...");
        TimeUnit.SECONDS.sleep(60);

        capture.start();
        String analysis = aiService.analyze(traceCount, "markdown");
        capture.stop();

        System.out.println("\n=== " + label + " ANALYSIS OUTPUT ===");
        System.out.println(analysis);
        System.out.println("=== END " + label + " ANALYSIS OUTPUT ===\n");

        assertNotNull(analysis, label + ": analysis should not be null");
        assertFalse(analysis.isBlank(), label + ": analysis should not be blank");
        assertTrue(analysis.length() > 100,
                label + ": analysis should be substantive (got " + analysis.length() + " chars)");

        String capturedContext = capture.toFormattedString();
        System.out.println("=== " + label + " CAPTURED TOOL OUTPUTS ===");
        System.out.println("Captured " + capture.getOutputs().size() + " tool outputs, context length: " + capturedContext.length());

        var strategy = new AnalysisEvaluationStrategy(mapper, chatModel, capturedContext, capture.getSystemPrompt());
        var sample = EvaluationSample.<String>builder()
                .withName(label.toLowerCase().replace(' ', '-'))
                .withParameter(label)
                .withExpectedOutput(criteria)
                .build();

        EvaluationReport<String> report = scorer.evaluate(
                new Samples<>(sample),
                params -> analysis,
                strategy
        );

        var result = report.evaluations().getFirst();
        double score = result.score() * 100.0;

        System.out.println("=== " + label + " EVALUATION ===");
        System.out.println("Score: " + score + "/100");
        System.out.println("  " + result.sample().name() + ": score=" + score
                + " passed=" + result.passed()
                + " explanation=" + result.explanation());
        System.out.println("=== END " + label + " EVALUATION ===\n");

        assertTrue(score >= 70.0,
                label + ": evaluation score should be >= 70 (got " + score + ")");
    }

    static String getSystemPrompt(String methodName) {
        try {
            Method method = DevMcpAiService.class.getMethod(methodName, String.class);
            var annotation = method.getAnnotation(dev.langchain4j.service.SystemMessage.class);
            return annotation != null ? String.join("\n", annotation.value()) : "";
        } catch (NoSuchMethodException e) {
            return "";
        }
    }

    void examineSourceCode(Runnable pokes, String criteria) throws Exception {
        pokes.run();

        String analysis = waitForAnalysis("SOURCE EXAMINATION", 2);

        System.out.println("[FullIntegrationTest] Calling examineSource...");
        String sources = devMcpAiService.examineSource(analysis);

        System.out.println("\n=== SOURCE EXAMINATION OUTPUT ===");
        System.out.println(sources);
        System.out.println("=== END SOURCE EXAMINATION OUTPUT ===\n");

        assertNotNull(sources, "Source examination should not be null");
        assertFalse(sources.isBlank(), "Source examination should not be blank");
        assertTrue(sources.length() > 200,
                "Source examination should be substantive (got " + sources.length() + " chars)");

        var strategy = new SourceExaminationEvaluationStrategy(mapper, chatModel, analysis, getSystemPrompt("examineSource"));
        var sample = EvaluationSample.<String>builder()
                .withName("source-examination")
                .withParameter("SOURCE EXAMINATION")
                .withExpectedOutput(criteria)
                .build();

        EvaluationReport<String> report = scorer.evaluate(
                new Samples<>(sample),
                params -> sources,
                strategy
        );

        var result = report.evaluations().getFirst();
        double score = result.score() * 100.0;

        System.out.println("=== SOURCE EXAMINATION EVALUATION ===");
        System.out.println("Score: " + score + "/100");
        System.out.println("  " + result.sample().name() + ": score=" + score
                + " passed=" + result.passed()
                + " explanation=" + result.explanation());
        System.out.println("=== END SOURCE EXAMINATION EVALUATION ===\n");

        assertTrue(score >= 70.0,
                "SOURCE EXAMINATION: evaluation score should be >= 70 (got " + score + ")");
    }

    void generateDashboard(Runnable pokes, String criteria) throws Exception {
        pokes.run();

        String analysis = waitForAnalysis("DASHBOARD GENERATION", 3);

        System.out.println("[FullIntegrationTest] Calling createDashboard...");
        devMcpTools.resetSaveTracking();
        String dashboard = sanitizeDashboardJson(mapper, devMcpAiService.createDashboard(analysis));
        devMcpTools.saveDashboardToUnsaved(dashboard);

        System.out.println("\n=== DASHBOARD GENERATION OUTPUT ===");
        System.out.println(dashboard);
        System.out.println("=== END DASHBOARD GENERATION OUTPUT ===\n");

        assertNotNull(dashboard, "Dashboard should not be null");
        assertFalse(dashboard.isBlank(), "Dashboard should not be blank");
        assertTrue(dashboard.contains("panels"), "Dashboard should contain panels");

        var strategy = new DashboardEvaluationStrategy(mapper, chatModel, analysis, getSystemPrompt("createDashboard"));
        var sample = EvaluationSample.<String>builder()
                .withName("dashboard-generation")
                .withParameter("DASHBOARD GENERATION")
                .withExpectedOutput(criteria)
                .build();

        EvaluationReport<String> report = scorer.evaluate(
                new Samples<>(sample),
                params -> dashboard,
                strategy
        );

        var result = report.evaluations().getFirst();
        double score = result.score() * 100.0;

        System.out.println("=== DASHBOARD GENERATION EVALUATION ===");
        System.out.println("Score: " + score + "/100");
        System.out.println("  " + result.sample().name() + ": score=" + score
                + " passed=" + result.passed()
                + " explanation=" + result.explanation());
        System.out.println("=== END DASHBOARD GENERATION EVALUATION ===\n");

        assertTrue(score >= 70.0,
                "DASHBOARD GENERATION: evaluation score should be >= 70 (got " + score + ")");
    }

}
