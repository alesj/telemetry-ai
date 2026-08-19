package io.quarkus.telemetry.ai;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import jakarta.inject.Singleton;

import java.util.function.Predicate;

public class Configuration {
    @Singleton
    public Predicate<InvocationContext> toolPredicate() {
        return ic -> {
            ModelProvider provider = ic.modelProvider();
            return provider == ModelProvider.WATSONX &&
                    "ibm/granite-4-h-small".equals(ic.defaultRequestParameters().modelName());
        };
    }
}
