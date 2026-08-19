package io.quarkus.telemetry.ai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusStreamableHttpMcpTransport;
import io.quarkus.runtime.Shutdown;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class DevMcpToolProviderSupplier implements Supplier<ToolProvider> {
    private static final Logger log = LoggerFactory.getLogger(DevMcpToolProviderSupplier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FILE_URI_PATTERN = Pattern.compile("file:///[^\"\\s]+/src/");

    @ConfigProperty(name = "app.ports")
    Optional<List<Integer>> appPorts;

    @Inject
    Vertx vertx;

    private final List<McpClient> devMcpClients = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> workspaceRoots = new ConcurrentHashMap<>();
    private volatile ToolProvider toolProvider;

    @Override
    public ToolProvider get() {
        if (toolProvider == null) {
            synchronized (this) {
                if (toolProvider == null) {
                    toolProvider = createToolProvider();
                }
            }
        }
        return toolProvider;
    }

    private ToolProvider createToolProvider() {
        if (appPorts.isPresent()) {
            for (int port : appPorts.get()) {
                String name = "dev-mcp-" + port;
                String url = "http://localhost:" + port + "/q/dev-mcp";
                log.info("Creating dev-mcp client: {} -> {}", name, url);
                McpClient devClient = new DefaultMcpClient.Builder()
                        .key(name)
                        .clientName(name)
                        .transport(new QuarkusStreamableHttpMcpTransport.Builder()
                                .mcpClientName(name)
                                .url(url)
                                .httpClient(vertx.createHttpClient())
                                .build())
                        .build();
                devMcpClients.add(devClient);
                fetchWorkspaceRoot(devClient);
            }
        } else {
            log.warn("Missing app.ports configuration, Quarkus dev-mcp cannot be used then.");
        }

        ToolProvider mcpProvider = McpToolProvider.builder()
                .mcpClients(devMcpClients)
                .filter((client, tool) -> tool.name().startsWith("devui-workspace_"))
                .toolNameMapper((client, tool) -> tool.name() + "_" + client.key())
                .build();

        return request -> {
            ToolProviderResult result = mcpProvider.provideTools(request);
            Map<ToolSpecification, ToolExecutor> wrapped = new LinkedHashMap<>();
            for (var entry : result.tools().entrySet()) {
                String toolName = entry.getKey().name();
                if (toolName.contains("saveWorkspaceItemContent") || toolName.contains("getWorkspaceItemContent")) {
                    String clientKey = extractClientKey(toolName);
                    wrapped.put(entry.getKey(), wrapPathFixExecutor(clientKey, entry.getValue()));
                } else {
                    wrapped.put(entry.getKey(), entry.getValue());
                }
            }
            return ToolProviderResult.builder().addAll(wrapped).build();
        };
    }

    private void fetchWorkspaceRoot(McpClient client) {
        try {
            ToolExecutionRequest req = ToolExecutionRequest.builder()
                    .name("devui-workspace_getWorkspaceItems")
                    .arguments("{}")
                    .build();
            var result = client.executeTool(req);
            String root = extractRoot(result.resultText());
            if (root != null) {
                workspaceRoots.put(client.key(), root);
                log.info("Workspace root for {}: {}", client.key(), root);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch workspace root for {}: {}", client.key(), e.getMessage());
        }
    }

    private ToolExecutor wrapPathFixExecutor(String clientKey, ToolExecutor delegate) {
        return (request, memoryId) -> {
            String args = request.arguments();
            if (args != null && args.contains("\"path\"")) {
                String fixed = fixPathArgument(clientKey, args);
                if (!fixed.equals(args)) {
                    log.info("Fixed saveWorkspaceItemContent path to file:/// URI");
                    request = ToolExecutionRequest.builder()
                            .id(request.id())
                            .name(request.name())
                            .arguments(fixed)
                            .build();
                }
            }
            return delegate.execute(request, memoryId);
        };
    }

    String fixPathArgument(String clientKey, String args) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(args);
            JsonNode pathNode = node.get("path");
            if (pathNode == null || pathNode.isNull()) {
                return args;
            }
            String path = pathNode.asText();
            if (path.startsWith("file:///")) {
                return args;
            }
            String fixedPath;
            if (path.startsWith("/")) {
                fixedPath = "file://" + path;
            } else if (clientKey != null && workspaceRoots.containsKey(clientKey)) {
                String root = workspaceRoots.get(clientKey);
                fixedPath = root + (root.endsWith("/") ? "" : "/") + path;
            } else {
                return args;
            }
            node.put("path", fixedPath);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("Failed to fix path argument: {}", e.getMessage());
            return args;
        }
    }

    static String extractRoot(String workspaceItemsResult) {
        Matcher m = FILE_URI_PATTERN.matcher(workspaceItemsResult);
        if (m.find()) {
            String match = m.group();
            int srcIdx = match.lastIndexOf("/src/");
            return match.substring(0, srcIdx);
        }
        return null;
    }

    static String extractClientKey(String toolName) {
        int lastUnderscore = toolName.lastIndexOf("_dev-mcp-");
        if (lastUnderscore >= 0) {
            return toolName.substring(lastUnderscore + 1);
        }
        return null;
    }

    @Shutdown
    void closeDevMcpClients() {
        for (McpClient client : devMcpClients) {
            try {
                log.info("Closing dev-mcp client: {}", client.key());
                client.close();
            } catch (Exception e) {
                log.warn("Failed to close dev-mcp client: {}", client.key(), e);
            }
        }
        devMcpClients.clear();
    }
}
