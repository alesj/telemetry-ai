package io.quarkus.telemetry.ai;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;

@RegisterAiService
public interface AiService {
    @McpToolBox({"tempo", "grafana"})
    String analyze(@UserMessage String message);
}
