package io.quarkus.telemetry.ai;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import java.util.function.Predicate;

public class Configuration {

    @Singleton
    public ToolProvider toolProvider(Instance<McpClient> clients) {
        return McpToolProvider.builder()
                .mcpClients(clients.stream().toList())
                .build();
    }

    @Singleton
    public Predicate<InvocationContext> toolPredicate() {
        return ic -> {
            ModelProvider provider = ic.modelProvider();
            return provider == ModelProvider.WATSONX &&
                    "ibm/granite-4-h-small".equals(ic.defaultRequestParameters().modelName());
        };
    }
}
