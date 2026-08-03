package io.quarkus.telemetry.ai.test;

import io.quarkus.telemetry.ai.AiService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void analyzeEndToEnd() throws Exception {
        pokeProxy(200);
        pokeProxy(500);
        pokeProxy(403);
        pokeProxy(200);

        System.out.println("[FullIntegrationTest] Waiting 60s for telemetry ingestion...");
        TimeUnit.SECONDS.sleep(60);

        String grafanaEndpoint = ConfigProvider.getConfig()
                .getValue("grafana.endpoint", String.class);
        String metricNames = CompanionApps.httpGet(
                grafanaEndpoint + "/api/datasources/proxy/uid/prometheus/api/v1/label/__name__/values",
                "admin", "admin");
        System.out.println("[FullIntegrationTest] Prometheus metric names: " + metricNames);

        String analysis = aiService.analyze(2);

        System.out.println("\n=== INTEGRATION TEST ANALYSIS OUTPUT ===");
        System.out.println(analysis);
        System.out.println("=== END INTEGRATION TEST ANALYSIS OUTPUT ===\n");

        assertNotNull(analysis, "Analysis should not be null");
        assertFalse(analysis.isBlank(), "Analysis should not be blank");
        assertTrue(analysis.length() > 100,
                "Analysis should be substantive (got " + analysis.length() + " chars)");
    }

    @AfterAll
    void stopCompanionApps() {
        proxyProcess.stop();
        appProcess.stop();
    }

    private void pokeProxy(int value) {
        String url = "http://localhost:" + PROXY_PORT + "/poke?value=" + value;
        int status = CompanionApps.pokeHttp(url);
        System.out.println("[FullIntegrationTest] Poked proxy value=" + value + " status=" + status);
    }
}
