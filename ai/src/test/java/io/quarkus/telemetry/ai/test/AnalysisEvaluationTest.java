package io.quarkus.telemetry.ai.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@QuarkusTest
@TestProfile(EvalTestProfile.class)
@EnabledIfSystemProperty(named = "eval.run", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalysisEvaluationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    ChatModel chatModel;

    private EvaluationJudge judge;
    private boolean judgeAvailable;
    private final List<EvaluationRecord> results = new ArrayList<>();

    private static final String MEMORY_PRESSURE_SPEC = """
            - MUST detect: HTTP 500 error
            - MUST detect: OutOfMemoryError in logs
            - MUST correlate: jvm_memory_used_bytes at >90% when error occurred
            - MUST identify root cause: Memory exhaustion
            - MUST recommend: Increase heap size or investigate memory leak
            - MUST NOT: Blame CPU, network, or unrelated metrics as the cause
            """;

    private static final String THREAD_STARVATION_SPEC = """
            - MUST detect: Slow response time (>2s)
            - MUST correlate: worker_pool_queue_size >0 with worker_pool_idle=0
            - MUST identify root cause: Thread pool exhaustion
            - MUST recommend: Increase worker thread pool size or reduce blocking operations
            - MUST NOT: Blame memory or CPU if they are normal
            """;

    private static final String DOWNSTREAM_ERROR_SPEC = """
            - MUST detect: HTTP 500 at proxy, HTTP 403 from downstream service
            - MUST identify: Error propagation from child span to parent
            - MUST quote: Relevant log message about 403 Forbidden
            - MUST correlate: System metrics normal (not a resource issue)
            - MUST identify root cause: Authorization failure in downstream service
            - MUST recommend: Check API credentials or permissions
            - MUST NOT: Blame CPU, memory, or thread pool
            """;

    private static final String HEALTHY_REQUEST_SPEC = """
            - MUST detect: HTTP 200 success
            - MUST report: Normal latency (<100ms)
            - MUST report: System metrics healthy
            - MUST NOT: Report false issues, warnings, or errors
            - MUST NOT: Claim any resource pressure or performance problems
            - Severity MUST be LOW
            """;

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
    void evaluateMemoryPressureAnalysis() throws IOException {
        EvaluationRecord result = evaluate(
                "Memory Pressure Error",
                "memory-pressure.md",
                MEMORY_PRESSURE_SPEC,
                "CRITICAL"
        );

        assertTrue(result.structural.passes(),
                "Structural validation failed. " + result.structural);

        if (judgeAvailable) {
            assertTrue(result.judge.passes(),
                    "Judge score below threshold. " + result.judge);
        }
    }

    @Test
    void evaluateThreadStarvationAnalysis() throws IOException {
        EvaluationRecord result = evaluate(
                "Thread Starvation",
                "thread-starvation.md",
                THREAD_STARVATION_SPEC,
                "HIGH"
        );

        assertTrue(result.structural.passes(),
                "Structural validation failed. " + result.structural);

        if (judgeAvailable) {
            assertTrue(result.judge.passes(),
                    "Judge score below threshold. " + result.judge);
        }
    }

    @Test
    void evaluateDownstreamErrorAnalysis() throws IOException {
        EvaluationRecord result = evaluate(
                "Downstream Service Error",
                "downstream-error.md",
                DOWNSTREAM_ERROR_SPEC,
                "HIGH"
        );

        assertTrue(result.structural.passes(),
                "Structural validation failed. " + result.structural);

        if (judgeAvailable) {
            assertTrue(result.judge.passes(),
                    "Judge score below threshold. " + result.judge);
        }
    }

    @Test
    void evaluateHealthyRequestAnalysis() throws IOException {
        EvaluationRecord result = evaluate(
                "Healthy Request (No False Positives)",
                "healthy-request.md",
                HEALTHY_REQUEST_SPEC,
                "LOW"
        );

        assertTrue(result.structural.passes(),
                "Structural validation failed. " + result.structural);

        if (judgeAvailable) {
            assertTrue(result.judge.passes(),
                    "Judge score below threshold. " + result.judge);
        }
    }

    @Test
    void llmJudgeMemoryPressure() throws IOException {
        assumeTrue(judgeAvailable, "LLM API key not configured");
        String analysis = loadFixture("memory-pressure.md");
        EvaluationJudge.JudgeScore score = judge.evaluate(
                "Memory Pressure Error", MEMORY_PRESSURE_SPEC, "CRITICAL", analysis);
        System.out.println("[LLM Judge] Memory Pressure: " + score);
        assertTrue(score.passes(), "LLM judge score below 70: " + score);
    }

    @Test
    void llmJudgeThreadStarvation() throws IOException {
        assumeTrue(judgeAvailable, "LLM API key not configured");
        String analysis = loadFixture("thread-starvation.md");
        EvaluationJudge.JudgeScore score = judge.evaluate(
                "Thread Starvation", THREAD_STARVATION_SPEC, "HIGH", analysis);
        System.out.println("[LLM Judge] Thread Starvation: " + score);
        assertTrue(score.passes(), "LLM judge score below 70: " + score);
    }

    @Test
    void llmJudgeDownstreamError() throws IOException {
        assumeTrue(judgeAvailable, "LLM API key not configured");
        String analysis = loadFixture("downstream-error.md");
        EvaluationJudge.JudgeScore score = judge.evaluate(
                "Downstream Service Error", DOWNSTREAM_ERROR_SPEC, "HIGH", analysis);
        System.out.println("[LLM Judge] Downstream Error: " + score);
        assertTrue(score.passes(), "LLM judge score below 70: " + score);
    }

    @Test
    void llmJudgeHealthyRequest() throws IOException {
        assumeTrue(judgeAvailable, "LLM API key not configured");
        String analysis = loadFixture("healthy-request.md");
        EvaluationJudge.JudgeScore score = judge.evaluate(
                "Healthy Request", HEALTHY_REQUEST_SPEC, "LOW", analysis);
        System.out.println("[LLM Judge] Healthy Request: " + score);
        assertTrue(score.passes(), "LLM judge score below 70: " + score);
    }

    @AfterAll
    void reportResults() {
        if (results.isEmpty()) return;

        System.out.println("\n=== EVALUATION REPORT ===");
        for (EvaluationRecord r : results) {
            System.out.println(r);
            System.out.println("---");
        }

        int structuralPasses = (int) results.stream().filter(r -> r.structural.passes()).count();
        System.out.printf("Structural: %d/%d passed%n", structuralPasses, results.size());

        if (judgeAvailable) {
            long judgePasses = results.stream().filter(r -> r.judge != null && r.judge.passes()).count();
            double avgScore = results.stream()
                    .filter(r -> r.judge != null)
                    .mapToInt(r -> r.judge.total())
                    .average()
                    .orElse(0);
            System.out.printf("LLM Judge: %d/%d passed, avg score: %.0f/100%n",
                    judgePasses, results.size(), avgScore);
        }

        saveResults();
    }

    private EvaluationRecord evaluate(String testCaseName, String fixtureFile,
                                      String expectedFindings, String expectedSeverity) throws IOException {
        String analysis = loadFixture(fixtureFile);

        StructuralValidator.Result structural = StructuralValidator.validate(analysis);

        EvaluationJudge.JudgeScore judgeScore = null;
        if (judgeAvailable) {
            judgeScore = judge.evaluate(testCaseName, expectedFindings, expectedSeverity, analysis);
        }

        EvaluationRecord record = new EvaluationRecord(testCaseName, fixtureFile, structural, judgeScore);
        results.add(record);

        System.out.printf("[%s] %s%n", testCaseName, structural);
        if (judgeScore != null) {
            System.out.printf("[%s] %s%n", testCaseName, judgeScore);
        }

        return record;
    }

    private static String loadFixture(String name) throws IOException {
        try (InputStream is = AnalysisEvaluationTest.class.getClassLoader()
                .getResourceAsStream("evaluation/recorded/" + name)) {
            assertNotNull(is, "Fixture file not found: evaluation/recorded/" + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void saveResults() {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("timestamp", Instant.now().toString());
            root.put("judgeAvailable", judgeAvailable);
            if (judgeAvailable) {
                root.put("judgeModel", System.getProperty("eval.model", "gpt-4o-mini"));
            }

            ArrayNode testCases = root.putArray("testCases");
            for (EvaluationRecord r : results) {
                ObjectNode tc = testCases.addObject();
                tc.put("name", r.testCaseName);
                tc.put("fixture", r.fixtureFile);

                ObjectNode structural = tc.putObject("structural");
                structural.put("sectionCount", r.structural.sectionCount());
                structural.put("totalSections", r.structural.totalSections());
                structural.put("score", r.structural.score());
                structural.put("maxScore", r.structural.maxScore());
                structural.put("passes", r.structural.passes());
                if (!r.structural.missingSections().isEmpty()) {
                    ArrayNode missing = structural.putArray("missingSections");
                    r.structural.missingSections().forEach(missing::add);
                }

                if (r.judge != null) {
                    ObjectNode judgeNode = tc.putObject("judge");
                    judgeNode.put("completeness", r.judge.completeness());
                    judgeNode.put("accuracy", r.judge.accuracy());
                    judgeNode.put("correlationQuality", r.judge.correlationQuality());
                    judgeNode.put("actionability", r.judge.actionability());
                    judgeNode.put("total", r.judge.total());
                    judgeNode.put("passes", r.judge.passes());
                    judgeNode.put("justification", r.judge.justification());
                }
            }

            Path resultsFile = Path.of("target/evaluation-results.json");
            Files.createDirectories(resultsFile.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(resultsFile.toFile(), root);
        } catch (IOException e) {
            System.err.println("Failed to save evaluation results: " + e.getMessage());
        }
    }

    record EvaluationRecord(
            String testCaseName,
            String fixtureFile,
            StructuralValidator.Result structural,
            EvaluationJudge.JudgeScore judge
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Test Case: %s (%s)%n", testCaseName, fixtureFile));
            sb.append("  ").append(structural).append("\n");
            if (judge != null) {
                sb.append("  ").append(judge).append("\n");
            }
            return sb.toString();
        }
    }
}
