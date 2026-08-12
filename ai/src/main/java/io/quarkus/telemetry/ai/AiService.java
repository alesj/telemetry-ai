package io.quarkus.telemetry.ai;

import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

@RegisterAiService(systemMessageProviderSupplier = StaticModelSystemMessageProvider.class)
public interface AiService {
    @ToolBox(PlainTools.class)
    String analyze(@V("n") int n, @V("outputType") String outputType, @V("createDashboard") boolean createDashboard);
}
