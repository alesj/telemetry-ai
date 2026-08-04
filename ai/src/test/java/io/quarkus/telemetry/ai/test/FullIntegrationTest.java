package io.quarkus.telemetry.ai.test;

import io.quarkus.telemetry.ai.AiService;
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

import org.eclipse.microprofile.config.ConfigProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
@EnabledIfSystemProperty(named = "integration.run", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 600, unit = TimeUnit.SECONDS)
class FullIntegrationTest {

    static final int APP_PORT = 8082;
    static final int PROXY_PORT = 8081;

    @Inject
    AiService aiService;

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

        waitAndAnalyze("NORMAL TRAFFIC", 2);
    }

    @Test
    @Order(2)
    void analyzeErrorTraffic() throws Exception {
        pokeProxy(500);
        pokeProxy(403);
        chaosProxy("error", null);
        pokeProxy(200);

        waitAndAnalyze("ERROR TRAFFIC", 3);
    }

    @Test
    @Order(3)
    void analyzeLatency() throws Exception {
        chaosProxy("delay", 5000);
        chaosProxy("delay", 3000);
        pokeProxy(200);

        waitAndAnalyze("LATENCY", 2);
    }

    @Test
    @Order(4)
    void analyzeResourcePressure() throws Exception {
        chaosProxy("memory", 100);
        chaosProxy("cpu", 3000);
        chaosProxy("leak", 50);
        chaosProxy("leak", 50);

        waitAndAnalyze("RESOURCE PRESSURE", 3);
    }

    @AfterAll
    void stopCompanionApps() {
        proxyProcess.stop();
        appProcess.stop();
    }

    private void waitAndAnalyze(String label, int traceCount) throws Exception {
        System.out.println("[FullIntegrationTest] Waiting 60s for telemetry ingestion (" + label + ")...");
        TimeUnit.SECONDS.sleep(60);

        String grafanaEndpoint = ConfigProvider.getConfig()
                .getValue("grafana.endpoint", String.class);
        String metricNames = CompanionApps.httpGet(
                grafanaEndpoint + "/api/datasources/proxy/uid/prometheus/api/v1/label/__name__/values",
                "admin", "admin");
        System.out.println("[FullIntegrationTest] Prometheus metric names: " + metricNames);

        String analysis = aiService.analyze(traceCount);

        System.out.println("\n=== " + label + " ANALYSIS OUTPUT ===");
        System.out.println(analysis);
        System.out.println("=== END " + label + " ANALYSIS OUTPUT ===\n");

        assertNotNull(analysis, label + ": analysis should not be null");
        assertFalse(analysis.isBlank(), label + ": analysis should not be blank");
        assertTrue(analysis.length() > 100,
                label + ": analysis should be substantive (got " + analysis.length() + " chars)");
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
