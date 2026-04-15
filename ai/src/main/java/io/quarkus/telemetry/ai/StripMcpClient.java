package io.quarkus.telemetry.ai;

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
import java.util.function.Function;

public class StripMcpClient implements McpClient {

    private final McpClient delegate;
    private final Function<ToolExecutionResult, ToolExecutionResult> fn;

    public StripMcpClient(McpClient delegate, Function<ToolExecutionResult, ToolExecutionResult> fn) {
        this.delegate = delegate;
        this.fn = fn;
    }

    @Override
    public String key() {
        return delegate.key();
    }

    @Override
    public List<ToolSpecification> listTools() {
        return delegate.listTools();
    }

    @Override
    public List<ToolSpecification> listTools(InvocationContext invocationContext) {
        return delegate.listTools(invocationContext);
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest executionRequest) {
        return executeTool(executionRequest, null);
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest executionRequest,
                                           InvocationContext invocationContext) {
        ToolExecutionResult result = delegate.executeTool(executionRequest, invocationContext);
        System.out.println("rawRes = " + result.resultText());
        return fn.apply(result);
    }

    @Override
    public List<McpResource> listResources() {
        return delegate.listResources();
    }

    @Override
    public List<McpResource> listResources(InvocationContext invocationContext) {
        return delegate.listResources(invocationContext);
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates() {
        return delegate.listResourceTemplates();
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates(InvocationContext invocationContext) {
        return delegate.listResourceTemplates(invocationContext);
    }

    @Override
    public McpReadResourceResult readResource(String uri) {
        return delegate.readResource(uri);
    }

    @Override
    public McpReadResourceResult readResource(String uri, InvocationContext invocationContext) {
        return delegate.readResource(uri, invocationContext);
    }

    @Override
    public List<McpPrompt> listPrompts() {
        return delegate.listPrompts();
    }

    @Override
    public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
        return delegate.getPrompt(name, arguments);
    }

    @Override
    public void checkHealth() {
        delegate.checkHealth();
    }

    @Override
    public void setRoots(List<McpRoot> roots) {
        delegate.setRoots(roots);
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
}