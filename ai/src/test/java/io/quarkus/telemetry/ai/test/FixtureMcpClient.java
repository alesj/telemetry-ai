package io.quarkus.telemetry.ai.test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.service.tool.ToolExecutionResult;

import java.util.List;
import java.util.Map;

class FixtureMcpClient implements McpClient {

    private final String name;
    private final Map<String, String> toolResponses;

    FixtureMcpClient(String name, Map<String, String> toolResponses) {
        this.name = name;
        this.toolResponses = toolResponses;
    }

    @Override
    public String key() {
        return name;
    }

    @Override
    public List<ToolSpecification> listTools() {
        return toolResponses.keySet().stream()
                .map(toolName -> ToolSpecification.builder()
                        .name(toolName)
                        .description("MCP tool: " + toolName)
                        .build())
                .toList();
    }

    @Override
    public List<ToolSpecification> listTools(InvocationContext ctx) {
        return listTools();
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest request) {
        String response = toolResponses.getOrDefault(request.name(), "{}");
        return ToolExecutionResult.builder().resultText(response).build();
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest request, InvocationContext ctx) {
        return executeTool(request);
    }

    @Override
    public List<McpResource> listResources() {
        return List.of();
    }

    @Override
    public List<McpResource> listResources(InvocationContext ctx) {
        return List.of();
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates() {
        return List.of();
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates(InvocationContext ctx) {
        return List.of();
    }

    @Override
    public McpReadResourceResult readResource(String uri) {
        return null;
    }

    @Override
    public McpReadResourceResult readResource(String uri, InvocationContext ctx) {
        return null;
    }

    @Override
    public List<McpPrompt> listPrompts() {
        return List.of();
    }

    @Override
    public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
        return null;
    }

    @Override
    public void checkHealth() {
    }

    @Override
    public void setRoots(List<McpRoot> roots) {
    }

    @Override
    public void close() {
    }
}
