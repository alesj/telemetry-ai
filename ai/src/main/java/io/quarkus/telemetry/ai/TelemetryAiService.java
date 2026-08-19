package io.quarkus.telemetry.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

@RegisterAiService(
        systemMessageProviderSupplier = StaticModelSystemMessageProvider.class,
        toolProviderSupplier = NoToolProviderSupplier.class // so we ignore dev-mcp tools
)
public interface TelemetryAiService {
    @ToolBox(PlainTools.class)
    @UserMessage("Analyze last {n} requests, output analysis report with proper {outputType}")
    String analyze(@V("n") int n, @V("outputType") String outputType);
}
