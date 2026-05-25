package io.quarkus.telemetry.ai;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

public class Configuration {

    @Singleton
    public ToolProvider toolProvider(Instance<McpClient> clients) {
        return McpToolProvider.builder()
                .mcpClients(clients.stream().toList())
                .build();
    }
}
