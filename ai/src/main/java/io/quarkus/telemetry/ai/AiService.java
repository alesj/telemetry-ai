package io.quarkus.telemetry.ai;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

@RegisterAiService(systemMessageProviderSupplier = PerModelSystemMessageProvider.class)
public interface AiService {
    @ToolBox(PlainTools.class)
    String analyze(int n);
}
