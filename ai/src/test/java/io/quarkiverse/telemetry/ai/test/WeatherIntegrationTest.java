package io.quarkiverse.telemetry.ai.test;

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
@TestProfile(IntegrationTestProfile.Weather.class)
@EnabledIfSystemProperty(named = "integration.run", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 600, unit = TimeUnit.SECONDS)
@Evaluate
class WeatherIntegrationTest extends AppsTestBase {

    static final int WEATHER_PORT = 8084;

    DevModeProcess extProcess;

    @BeforeAll
    void startCompanionApps() {
        ToxiproxySetup.startWeatherOnly();
        extProcess = CompanionApps.startDevMode("ext", WEATHER_PORT,
                "weather-api.url=" + ToxiproxySetup.weatherApiUrl(),
                "quarkus.datasource.active=false",
                "quarkus.hibernate-orm.active=false");
    }

    @Test
    @Order(1)
    void analyzeWeatherNormal() throws Exception {
        pokeWeather("london", 3);
        pokeWeather("paris", 2);
        pokeWeather("tokyo", 3);

        String criteria = """
                The traces show HTTP requests to /weather and /v1/forecast endpoints.
                All requests completed successfully with HTTP 200 status codes.
                Response times are within normal range (under 1 second).
                No errors or exceptions in the traces.
                Analysis should report healthy, normal operation.""";

        waitAndAnalyze("WEATHER NORMAL", 3, criteria);
    }

    @Test
    @Order(2)
    void analyzeWeatherSlowApi() throws Exception {
        ToxiproxySetup.addWeatherLatency(3000);
        try {
            pokeWeather("london", 3);
            pokeWeather("berlin", 5);
        } finally {
            ToxiproxySetup.removeWeatherLatency();
        }

        String criteria = """
                The traces show HTTP requests with significantly elevated durations, in the range of 3 seconds or more.
                There are no HTTP errors, but performance is clearly degraded.
                Analysis should identify the latency and flag it as a performance concern.""";

        waitAndAnalyze("WEATHER SLOW API", 2, criteria);
    }

    @Test
    @Order(3)
    void analyzeWeatherApiOutage() throws Exception {
        ToxiproxySetup.cutWeatherConnection();
        try {
            pokeWeather("london", 3);
            pokeWeather("paris", 2);
            pokeWeather("tokyo", 3);
        } finally {
            ToxiproxySetup.restoreWeatherConnection();
        }

        pokeWeather("berlin", 2);
        pokeWeather("sydney", 3);

        String criteria = """
                Some traces show failed requests with error status or exceptions on /weather or /v1/forecast.
                The failures are caused by the external weather API being unreachable (connection errors or timeouts).
                After the outage, subsequent requests completed successfully with HTTP 200.
                The analysis should identify external service unavailability as the root cause.""";

        waitAndAnalyze("WEATHER API OUTAGE", 5, criteria);
    }

    @AfterAll
    void stopCompanionApps() {
        if (extProcess != null) {
            extProcess.stop();
        }
        ToxiproxySetup.stopWeatherOnly();
    }

    private void pokeWeather(String city, int days) {
        String url = "http://localhost:" + WEATHER_PORT + "/weather?city=" + city + "&days=" + days;
        int status = CompanionApps.pokeHttp(url);
        System.out.println("[WeatherIntegrationTest] Poked weather city=" + city + " days=" + days + " status=" + status);
    }
}
