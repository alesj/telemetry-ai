package io.quarkus.telemetry.ai;

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import jakarta.inject.Singleton;

import java.util.function.Supplier;

@Singleton
public class NoToolProviderSupplier implements Supplier<ToolProvider> {
    @Override
    public ToolProvider get() {
        return request -> ToolProviderResult.builder().build();
    }
}
