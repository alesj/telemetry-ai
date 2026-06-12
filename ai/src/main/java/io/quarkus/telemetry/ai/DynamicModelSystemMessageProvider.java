package io.quarkus.telemetry.ai;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Use this SMP when you use different models in the app.
 */
public class DynamicModelSystemMessageProvider extends BaseModelSystemMessageProvider {

    private final Map<String, String> msgMap = new ConcurrentHashMap<>();

    @Nullable
    protected String getSystemMessage(String key) {
        return msgMap.computeIfAbsent(key, this::loadSystemMessage);
    }
}
