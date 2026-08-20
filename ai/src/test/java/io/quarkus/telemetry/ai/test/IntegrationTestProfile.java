package io.quarkus.telemetry.ai.test;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;
import java.util.Optional;

public class IntegrationTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "test.fixture-mcp", "false",
                "quarkus.observability.enabled-in-tests", "true",
                "quarkus.http.test-port", "0"
        );
    }

    @Override
    public String getConfigProfile() {
        String ai = System.getenv("AI");
        if (ai == null) {
            ai = System.getProperty("ai");
        }
        return Optional.ofNullable(ai).orElse("openai");
    }
}
