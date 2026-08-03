package io.quarkus.telemetry.ai.test;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@IfBuildProperty(name = "test.fixture-mcp", stringValue = "true")
public class EvalMcpProducers {

    @Produces
    @Singleton
    @Named("tempoMcpClient")
    McpClient tempoMcpClient() {
        return new FixtureMcpClient("tempo", Map.of(
                "traceql-search", loadFixture("evaluation/mcp/traceql-search.json"),
                "get-trace", loadFixture("evaluation/mcp/get-trace.json")
        ));
    }

    @Produces
    @Singleton
    @Named("grafanaMcpClient")
    McpClient grafanaMcpClient() {
        return new FixtureMcpClient("grafana", Map.of(
                "query_loki_logs", loadFixture("evaluation/mcp/query_loki_logs.json"),
                "query_prometheus", loadFixture("evaluation/mcp/query_prometheus.json")
        ));
    }

    private static String loadFixture(String path) {
        try (InputStream is = EvalMcpProducers.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load fixture: " + path, e);
        }
    }
}
