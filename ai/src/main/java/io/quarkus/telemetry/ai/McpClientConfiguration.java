package io.quarkus.telemetry.ai;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusStreamableHttpMcpTransport;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.vertx.core.Vertx;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@UnlessBuildProfile("test")
public class McpClientConfiguration {
    private static final Logger log = LoggerFactory.getLogger(McpClientConfiguration.class);

    @ConfigProperty(name = "tempo-mcp.endpoint")
    String tempoMcpEndpoint;

    @ConfigProperty(name = "grafana.endpoint")
    String grafanaEndpoint;

    @Singleton
    @Named("tempoMcpClient")
    public McpClient tempoMcpClient(Vertx vertx) {
        McpTransport transport = new QuarkusStreamableHttpMcpTransport.Builder()
                .mcpClientName("tempo")
                .url(tempoMcpEndpoint + "/api/mcp")
                .httpClient(vertx.createHttpClient())
                .build();
        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .clientName("tempo")
                .transport(transport)
                .build();
        return new StripMcpClient(client, StripFunctions.TRACE);
    }

    @Singleton
    @Named("grafanaMcpClient")
    public McpClient grafanaMcpClient() {
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of("uvx", "mcp-grafana"))
                .environment(Map.of("GRAFANA_URL", grafanaEndpoint))
                .logEvents(true)
                .build();
        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .clientName("grafana")
                .transport(transport)
                .build();

        Map<String, Function<ToolExecutionResult, ToolExecutionResult>> toolSpecificFns = Map.of(
                "query_loki_logs", StripFunctions.LOG_DATA,
                "query_prometheus", StripFunctions.METRICS
        );

        return new StripMcpClient(client, Function.identity(), toolSpecificFns);
    }

    public void destroyMcpClient(@Disposes McpClient client) throws Exception {
        log.info("Closing the mcp client: " + client.key());
        client.close();
    }
}
