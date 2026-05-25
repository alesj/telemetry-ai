package io.quarkus.telemetry.ai.test;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;
import java.util.Optional;

public class EvalTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        String apiKey = Optional.ofNullable(System.getProperty("eval.api.key"))
                .or(() -> Optional.ofNullable(System.getenv("OPENAI_API_KEY")))
                .orElse("dummy-key-for-startup");

        String model = System.getProperty("eval.model", "gpt-4o-mini");

        return Map.of(
                "tempo-mcp.endpoint", "http://localhost:0",
                "grafana.endpoint", "http://localhost:0",
                "quarkus.langchain4j.openai.api-key", apiKey,
                "quarkus.langchain4j.openai.chat-model.model-name", model,
                "quarkus.http.test-port", "0"
        );
    }
}
