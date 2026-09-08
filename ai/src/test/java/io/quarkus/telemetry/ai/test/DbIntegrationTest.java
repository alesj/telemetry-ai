package io.quarkus.telemetry.ai.test;

import io.quarkiverse.langchain4j.evaluation.junit5.Evaluate;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
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

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
@EnabledIfSystemProperty(named = "integration.run", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 600, unit = TimeUnit.SECONDS)
@Evaluate
class DbIntegrationTest extends AppsTestBase {

    static final int DB_PORT = 8083;

    DevModeProcess dbProcess;

    @BeforeAll
    void startCompanionApps() {
        ToxiproxySetup.start();
        dbProcess = CompanionApps.startDevMode("db", DB_PORT,
                "quarkus.datasource.jdbc.url=" + ToxiproxySetup.jdbcUrl(),
                "quarkus.datasource.username=root",
                "quarkus.datasource.password=root");
    }

    @Test
    @Order(1)
    void analyzeNormalTraffic() throws Exception {
        pokeDb("surname", "Johnson");
        pokeDb("surname", "Johnson");
        pokeDb("name", "John");
        pokeDb("surname", "Smith");

        String criteria = """
                All requests completed successfully with HTTP 200 status codes.
                The requests are database queries for persons by surname and name.
                No errors, no latency spikes, no resource issues.
                Analysis should report healthy system state with normal operation.""";

        waitAndAnalyze("DB NORMAL TRAFFIC", 2, criteria);
    }

    @Test
    @Order(2)
    void analyzeSlowQueries() throws Exception {
        ToxiproxySetup.addLatency(3000);
        try {
            pokeDb("surname", "Johnson");
            pokeDb("name", "Alice");
            pokeDb("surname", "Smith");
            pokeDb("age", "28");
        } finally {
            ToxiproxySetup.removeLatency();
        }

        String criteria = """
                Some requests completed with significantly elevated latency or slow response times.
                The analysis should detect abnormally slow response times or degraded performance.
                There are no application code errors, but performance is degraded.
                The root cause should point to database or infrastructure-level slowness.""";

        waitAndAnalyze("DB SLOW QUERIES", 2, criteria);
    }

    @Test
    @Order(3)
    void analyzeDbOutage() throws Exception {
        ToxiproxySetup.cutConnection();
        try {
            pokeDb("surname", "Johnson");
            pokeDb("name", "Alice");
            pokeDb("surname", "Smith");
        } finally {
            ToxiproxySetup.restoreConnection();
        }

        // Recovery requests after connection restored
        pokeDb("age", "28");
        pokeDb("surname", "Johnson");

        String criteria = """
                Some requests failed or timed out due to database unavailability.
                The analysis should detect connection failures, timeouts, or database errors.
                After recovery, subsequent requests completed successfully.
                The root cause should point to database unavailability or infrastructure failure.""";

        waitAndAnalyze("DB OUTAGE", 3, criteria);
    }

    @AfterAll
    void stopCompanionApps() {
        dbProcess.stop();
        ToxiproxySetup.stop();
    }

    private void pokeDb(String param, String value) {
        String url = "http://localhost:" + DB_PORT + "/poke?" + param + "=" + value;
        int status = CompanionApps.pokeHttp(url);
        System.out.println("[DbIntegrationTest] Poked db " + param + "=" + value + " status=" + status);
    }
}
