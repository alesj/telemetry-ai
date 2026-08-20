package io.quarkus.telemetry.ai;

import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import jakarta.inject.Singleton;

import java.lang.reflect.Field;

@Singleton
public class GrokChatModelCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {

    @Override
    public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
        apply(builder);
    }

    static void apply(OpenAiChatModel.OpenAiChatModelBuilder builder) {
        String url = getBaseUrl(builder);
        if (url != null && url.contains("x.ai")) {
            builder.presencePenalty(null);
            builder.frequencyPenalty(null);
        }
    }

    // workaround so we get the right base url
    private static String getBaseUrl(OpenAiChatModel.OpenAiChatModelBuilder builder) {
        try {
            Class<?> clazz = builder.getClass();
            while (clazz != null) {
                try {
                    Field f = clazz.getDeclaredField("baseUrl");
                    f.setAccessible(true);
                    return (String) f.get(builder);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
