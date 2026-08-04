package io.quarkus.telemetry.ai;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProviderWithContext;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class BaseModelSystemMessageProvider implements SystemMessageProviderWithContext {

    protected final Logger log = Logger.getLogger(getClass());

    protected Consumer<String> capture() {
        InstanceHandle<ToolOutputCapture> instance = Arc.container().instance(ToolOutputCapture.class);
        return instance.isAvailable() ? instance.get() : s -> {};
    }

    @Override
    public Optional<String> getSystemMessage(InvocationContext context) {
        ModelProvider provider = context.modelProvider();
        String model = context.defaultRequestParameters().modelName();
        String key = String.format("%s_%s", provider, model).toLowerCase(Locale.ROOT);
        String msg = getSystemMessage(key);
        return Optional.of(msg);
    }

    protected abstract String getSystemMessage(String key);

    @Nullable
    protected String loadSystemMessage(String key) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(key + ".txt");
        if (is != null) {
            try (is) {
                log.infof("Found %s system message.", key);
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.info("Could not read system message for: " + key, e);
            }
        }
        log.infof("System message for %s not found, fallback to default.", key);
        return loadSystemMessage("default");
    }
}
