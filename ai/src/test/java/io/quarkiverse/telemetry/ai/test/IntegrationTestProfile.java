package io.quarkiverse.telemetry.ai.test;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class IntegrationTestProfile implements QuarkusTestProfile {

    protected String appPorts() {
        return null;
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        config.put("test.fixture-mcp", "false");
        config.put("quarkus.observability.enabled-in-tests", "true");
        config.put("quarkus.http.test-port", "0");
        String ports = appPorts();
        if (ports != null) {
            config.put("app.ports", ports);
        }
        return config;
    }

    public static class Chaos extends IntegrationTestProfile {
        @Override
        protected String appPorts() {
            return "8081,8082";
        }
    }

    public static class Db extends IntegrationTestProfile {
        @Override
        protected String appPorts() {
            return "8083";
        }
    }

    public static class Weather extends IntegrationTestProfile {
        @Override
        protected String appPorts() {
            return "8084";
        }
    }

    @Override
    public String getConfigProfile() {
        String ai = System.getenv("AI");
        if (ai == null) {
            ai = System.getProperty("ai");
        }
        String profile = Optional.ofNullable(ai).orElse("openai");
        System.out.printf("INFO: IntegrationTestProfile using AI provider: %s (env.AI=%s, sys.ai=%s)%n",
                profile, System.getenv("AI"), System.getProperty("ai"));
        return profile;
    }
}
