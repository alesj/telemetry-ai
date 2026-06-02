package io.quarkus.telemetry.ai.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.telemetry.ai.AiService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(EvalTestProfile.class)
@EnabledIfSystemProperty(named = "eval.run", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveAnalysisEvaluationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String EXPECTED_FINDINGS = """
            - MUST detect: HTTP 500 error
            - MUST detect: Downstream service call to port 8082 returned 500
            - MUST detect: ClientWebApplicationException or WebApplicationException in trace events
            - MUST correlate: System metrics normal (CPU <10%, memory <50%, no thread starvation)
            - MUST identify: Error propagation from downstream app to proxy
            - Severity: HIGH or CRITICAL
            """;

    @Inject
    AiService aiService;

    @Inject
    ChatModel chatModel;

    private EvaluationJudge judge;
    private boolean judgeAvailable;

    @BeforeAll
    void setup() {
        String apiKey = Optional.ofNullable(System.getProperty("eval.api.key"))
                .or(() -> Optional.ofNullable(System.getenv("OPENAI_API_KEY")))
                .orElse(null);
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals("dummy-key-for-startup")) {
            judge = new EvaluationJudge(chatModel);
            judgeAvailable = true;
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void analyzeEndToEnd() {
        String analysis = aiService.analyze(1);

        assertNotNull(analysis, "AiService.analyze(1) returned null");
        assertFalse(analysis.isBlank(), "AiService.analyze(1) returned blank");

        System.out.println("\n=== LIVE ANALYSIS OUTPUT ===");
        System.out.println(analysis);
        System.out.println("=== END LIVE ANALYSIS OUTPUT ===\n");

        StructuralValidator.Result structural = StructuralValidator.validate(analysis);
        System.out.println("[Live] " + structural);

        assertTrue(structural.passes(),
                "Structural validation failed. " + structural);

        if (judgeAvailable) {
            EvaluationJudge.JudgeScore judgeScore = judge.evaluate(
                    "Live Downstream Error (HTTP 500)", EXPECTED_FINDINGS, "HIGH", analysis);
            System.out.println("[Live] " + judgeScore);

            saveResults(analysis, structural, judgeScore);

            assertTrue(judgeScore.passes(),
                    "LLM judge score below threshold. " + judgeScore);
        } else {
            saveResults(analysis, structural, null);
        }
    }

    private void saveResults(String analysis, StructuralValidator.Result structural,
                             EvaluationJudge.JudgeScore judgeScore) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("timestamp", Instant.now().toString());
            root.put("testType", "live-analyze");
            root.put("judgeAvailable", judgeAvailable);

            ObjectNode structuralNode = root.putObject("structural");
            structuralNode.put("sectionCount", structural.sectionCount());
            structuralNode.put("totalSections", structural.totalSections());
            structuralNode.put("score", structural.score());
            structuralNode.put("maxScore", structural.maxScore());
            structuralNode.put("passes", structural.passes());

            if (judgeScore != null) {
                ObjectNode judgeNode = root.putObject("judge");
                judgeNode.put("completeness", judgeScore.completeness());
                judgeNode.put("accuracy", judgeScore.accuracy());
                judgeNode.put("correlationQuality", judgeScore.correlationQuality());
                judgeNode.put("actionability", judgeScore.actionability());
                judgeNode.put("total", judgeScore.total());
                judgeNode.put("passes", judgeScore.passes());
                judgeNode.put("justification", judgeScore.justification());
            }

            root.put("analysisLength", analysis.length());

            Path resultsFile = Path.of("target/live-evaluation-results.json");
            Files.createDirectories(resultsFile.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(resultsFile.toFile(), root);
        } catch (IOException e) {
            System.err.println("Failed to save live evaluation results: " + e.getMessage());
        }
    }
}
