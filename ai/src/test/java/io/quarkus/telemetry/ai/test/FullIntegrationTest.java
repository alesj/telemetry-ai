package io.quarkus.telemetry.ai.test;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.evaluation.junit5.Evaluate;
import io.quarkiverse.langchain4j.evaluation.junit5.ScorerConfiguration;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.telemetry.ai.DevMcpAiService;
import io.quarkus.telemetry.ai.DevMcpToolProviderSupplier;
import io.quarkus.telemetry.ai.TelemetryAiService;
import io.quarkus.telemetry.ai.ToolOutputCapture;

import static io.quarkus.telemetry.ai.DashboardUtils.sanitizeDashboardJson;
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

import java.lang.reflect.Method;
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
    DevMcpAiService devMcpAiService;

    @Inject
    DevMcpToolProviderSupplier devMcpTools;

    @Inject
    ObjectMapper mapper;

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

    @Test
    @Order(7)
    void analyzeIntermittentFailures() throws Exception {
        for (int i = 0; i < 20; i++) {
            chaosProxy("intermittent", 60);
        }

        String criteria = """
                High failure rate (~60%%) across 20 requests to the same /chaos endpoint.
                Analysis should detect:
                (1) Multiple HTTP 5xx error responses (500, 502, or 503) across the analyzed traces
                (2) Some successful HTTP 200 responses mixed in — not all requests fail
                (3) Identify that the errors are application-level (WebApplicationException),
                    not caused by resource pressure (CPU/memory should be normal)
                (4) Report the severity as at least MEDIUM due to repeated failures""";

        waitAndAnalyze("INTERMITTENT FAILURES", 8, criteria);
    }

    @Test
    @Order(8)
    void analyzeNetworkPartition() throws Exception {
        pokeProxy(200);
        pokeProxy(200);

        System.out.println("[FullIntegrationTest] Stopping APP to simulate network partition...");
        appProcess.stop();

        for (int i = 0; i < 3; i++) {
            pokeProxy(200);
        }

        System.out.println("[FullIntegrationTest] Restarting APP after partition...");
        appProcess = CompanionApps.startDevMode("app", APP_PORT);

        pokeProxy(200);
        pokeProxy(200);

        String criteria = """
                Network partition scenario: app was stopped mid-test, causing proxy errors,
                then restarted. Analysis should detect:
                (1) Successful requests before the outage
                (2) Connection errors during the partition (connection refused, timeout,
                    or 5xx from proxy when downstream is unreachable)
                (3) Recovery after restart (successful requests resume)
                (4) Identify this as an infrastructure/availability issue, not an application bug
                (5) Recommend health checks, circuit breakers, or retry policies""";

        waitAndAnalyze("NETWORK PARTITION", 5, criteria);
    }

    @Test
    @Order(9)
    void analyzeRequestFlood() throws Exception {
        chaosProxy("delay", 5000);
        chaosProxy("delay", 4000);
        chaosProxy("delay", 3000);
        pokeProxy(500);

        String criteria = """
                Three requests with injected delays (3-5 seconds each) followed by
                an HTTP 500 error request.
                Analysis should detect:
                (1) High latency in the chaos/delay traces — span durations of 3-5 seconds
                    caused by Thread.sleep delays, with log messages confirming the delay
                (2) The HTTP 500 error response in at least one trace
                (3) Report at least MEDIUM severity due to high latency or error presence""";

        waitAndAnalyze("REQUEST FLOOD", 4, criteria);
    }

    @Test
    @Order(10)
    void analyzeDeadlock() throws Exception {
        chaosProxy("deadlock", 8000);
        chaosProxy("contention", 3000);

        String criteria = """
                A deadlock was injected: two threads each hold one lock and wait for the other,
                causing a timeout. A lock contention request follows for contrast.
                Analysis should detect:
                (1) The deadlock trace: high latency (~8 seconds from the timeout), with log
                    messages mentioning "DEADLOCK DETECTED" and "permanently blocked"
                (2) Distinguish the deadlock from simple contention — deadlock logs mention
                    two threads waiting for each other's locks, not just serialized access
                (3) Report at least HIGH severity for the deadlock due to permanently blocked threads""";

        waitAndAnalyze("DEADLOCK", 2, criteria);
    }

    @Test
    @Order(11)
    void examineSourceCode() throws Exception {
        chaosProxy("exception", null);
        chaosProxy("delay", 5000);

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

        String criteria = """
                Source examination of an exception and a delay chaos injection.
                The examination should:
                (1) Reference the PokeResource.java source file (or equivalent handler class)
                (2) Identify the chaosException() method as the source of the RuntimeException
                    and include a code snippet showing the throw statement
                (3) Identify the chaosDelay() method as the source of the Thread.sleep latency
                    and include a code snippet showing the sleep call
                (4) Provide a suggested fix or note that the behavior is intentional (chaos testing)
                (5) Include an architecture overview or summary section""";

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

    @Test
    @Order(12)
    void generateDashboard() throws Exception {
        chaosProxy("cpu", 3000);
        chaosProxy("memory", 100);
        pokeProxy(500);

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

        String criteria = """
                Dashboard JSON generated from an analysis of CPU burn, memory allocation,
                and an HTTP 500 error. The dashboard should:
                (1) Be valid JSON with a top-level "title" and "panels" array
                (2) Include at least one panel with a PromQL expression for CPU
                    (e.g., process_cpu_usage or system_cpu_usage)
                (3) Include at least one panel with a PromQL expression for memory
                    (e.g., jvm_memory_used_bytes or similar)
                (4) Have at least 3 panels total with meaningful titles""";

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

    @AfterAll
    void stopCompanionApps() {
        proxyProcess.stop();
        appProcess.stop();
    }

    private String waitForAnalysis(String label, int traceCount) throws Exception {
        System.out.println("[FullIntegrationTest] Waiting 60s for telemetry ingestion (" + label + ")...");
        TimeUnit.SECONDS.sleep(60);

        String analysis = aiService.analyze(traceCount, "markdown");

        System.out.println("\n=== " + label + " ANALYSIS OUTPUT ===");
        System.out.println(analysis);
        System.out.println("=== END " + label + " ANALYSIS OUTPUT ===\n");

        assertNotNull(analysis, label + ": analysis should not be null");
        assertFalse(analysis.isBlank(), label + ": analysis should not be blank");
        return analysis;
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

    private static String getSystemPrompt(String methodName) {
        try {
            Method method = DevMcpAiService.class.getMethod(methodName, String.class);
            var annotation = method.getAnnotation(dev.langchain4j.service.SystemMessage.class);
            return annotation != null ? String.join("\n", annotation.value()) : "";
        } catch (NoSuchMethodException e) {
            return "";
        }
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
