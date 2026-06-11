package io.quarkus.telemetry.ai;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProviderWithContext;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PerModelSystemMessageProvider implements SystemMessageProviderWithContext {

    private static final Logger log = Logger.getLogger(PerModelSystemMessageProvider.class);

    private final Map<String, String> msgMap = new ConcurrentHashMap<>();

    @Override
    public Optional<String> getSystemMessage(InvocationContext context) {
        ModelProvider provider = context.modelProvider();
        String model = context.defaultRequestParameters().modelName();
        String key = String.format("%s_%s", provider, model).toLowerCase(Locale.ROOT);
        return Optional.of(getSystemMessage(key));
    }

    @Nullable
    private String getSystemMessage(String key) {
        String msg = msgMap.computeIfAbsent(key, k -> {
            InputStream is = getClass().getClassLoader().getResourceAsStream(k + ".txt");
            if (is != null) {
                try (is) {
                    log.infof("Found %s system message.", k);
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    log.info("Could not read system message for: " + k, e);
                }
            }
            return null;
        });
        if (msg == null) {
            log.infof("System message for %s not found, fallback to default.", k);
            return getSystemMessage("default");
        } else {
            return msg;
        }
    }
}
