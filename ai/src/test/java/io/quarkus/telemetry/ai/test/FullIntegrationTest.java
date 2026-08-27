package io.quarkus.telemetry.ai.test;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.evaluation.junit5.Evaluate;
import io.quarkiverse.langchain4j.evaluation.junit5.ScorerConfiguration;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkus.telemetry.ai.TelemetryAiService;
import io.quarkus.telemetry.ai.ToolOutputCapture;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
@EnabledIfSystemProperty(named = "integration.run", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 600, unit = TimeUnit.SECONDS)
@Evaluate
class FullIntegrationTest {

    static final int APP_PORT = 8082;
    static final int PROXY_PORT = 8081;

    @Inject
    TelemetryAiService aiService;

    @Inject
    ToolOutputCapture capture;

    @Inject
    @ModelName("scorer")
    ChatModel chatModel;

    @ScorerConfiguration
    Scorer scorer;

    DevModeProcess appProcess;
    DevModeProcess proxyProcess;

    @BeforeAll
    void startCompanionApps() {
        appProcess = CompanionApps.startDevMode("app", APP_PORT);
        proxyProcess = CompanionApps.startDevMode("proxy", PROXY_PORT,
                "quarkus.rest-client.proxy.url=http://localhost:" + APP_PORT);
    }

    @Test
    @Order(1)
    void analyzeNormalTraffic() throws Exception {
        pokeProxy(200);
        pokeProxy(200);
        pokeProxy(200);
        pokeProxy(200);

        String criteria = """
                All requests completed successfully with HTTP 200/204 status codes.
                No errors, no latency spikes, no resource issues.
                Analysis should report healthy system state with normal operation.""";

        waitAndAnalyze("NORMAL TRAFFIC", 2, criteria);
    }

    @Test
    @Order(2)
    void analyzeErrorTraffic() throws Exception {
        pokeProxy(500);
        pokeProxy(403);
        chaosProxy("error", null);
        pokeProxy(200);

        String criteria = """
                Multiple HTTP error responses present: 500 Internal Server Error, 403 Forbidden,
                and random 5xx chaos errors. Analysis should identify error patterns,
                mention specific error codes, and flag error rate as a concern.""";

        waitAndAnalyze("ERROR TRAFFIC", 3, criteria);
    }

    @Test
    @Order(3)
    void analyzeLatency() throws Exception {
        chaosProxy("delay", 5000);
        chaosProxy("delay", 3000);
        pokeProxy(200);

        String criteria = """
                Trace spans with abnormally high duration (~5 seconds and ~3 seconds)
                caused by Thread.sleep delays. Analysis should identify latency spikes,
                flag slow operations, and note the unusually long response times.""";

        waitAndAnalyze("LATENCY", 3, criteria);
    }

    @Test
    @Order(4)
    void analyzeResourcePressure() throws Exception {
        chaosProxy("memory", 100);
        chaosProxy("cpu", 3000);
        chaosProxy("leak", 50);
        chaosProxy("leak", 50);

        String criteria = """
                Resource pressure scenarios: 100MB memory allocation spike, 3-second CPU burn,
                and 2x 50MB memory leaks (never freed). Analysis should identify resource concerns
                such as memory usage, CPU pressure, or heap growth in traces/logs/metrics.""";

        waitAndAnalyze("RESOURCE PRESSURE", 3, criteria);
    }

    @Test
    @Order(5)
    void analyzeCascadingFailure() throws Exception {
        chaosProxy("exception", null);
        chaosProxy("threadpool", 5000);
        chaosProxy("gc", 200);
        pokeProxy(200);

        String criteria = """
                Multiple chaos types injected. Analysis should individually identify each:
                (1) The exception trace: report HTTP 500 status, find the RuntimeException
                    or stack trace in logs, and report the exception class and message.
                (2) The threadpool trace: report that 10 threads were blocked for 5000ms
                    from the log line, flag as thread pool saturation, note span duration >5s.
                (3) The GC trace: report GC churn of 200MB from the log line, flag as GC
                    pressure, note heap usage from logs.
                Each chaos trace should have severity at least MEDIUM.""";

        waitAndAnalyze("CASCADING FAILURE", 4, criteria);
    }

    @Test
    @Order(6)
    void analyzeLockContention() throws Exception {
        chaosProxy("contention", 5000);
        chaosProxy("contention", 3000);

        String criteria = """
                Lock contention chaos was injected. Analysis should identify:
                (1) High latency in the chaos/contention traces (span durations of several seconds)
                (2) Mention lock contention, thread blocking, or synchronization issues
                    based on log messages or span analysis
                The analysis passes if it reports abnormal latency and attributes it to
                contention, locking, or thread blocking.""";

        waitAndAnalyze("LOCK CONTENTION", 2, criteria);
    }

    @AfterAll
    void stopCompanionApps() {
        proxyProcess.stop();
        appProcess.stop();
    }

    private void waitAndAnalyze(String label, int traceCount, String criteria) throws Exception {
        System.out.println("[FullIntegrationTest] Waiting 60s for telemetry ingestion (" + label + ")...");
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

        var strategy = new AnalysisEvaluationStrategy(chatModel, capturedContext, capture.getSystemPrompt());
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

    private void pokeProxy(int value) {
        String url = "http://localhost:" + PROXY_PORT + "/poke?value=" + value;
        int status = CompanionApps.pokeHttp(url);
        System.out.println("[FullIntegrationTest] Poked proxy value=" + value + " status=" + status);
    }

    private void chaosProxy(String type, Integer intensity) {
        String url = "http://localhost:" + PROXY_PORT + "/chaos?type=" + type;
        if (intensity != null) {
            url += "&intensity=" + intensity;
        }
        int status = CompanionApps.pokeHttp(url);
        System.out.println("[FullIntegrationTest] Chaos type=" + type + " intensity=" + intensity + " status=" + status);
    }
}
