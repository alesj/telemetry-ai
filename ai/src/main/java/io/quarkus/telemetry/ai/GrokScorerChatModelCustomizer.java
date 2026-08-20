package io.quarkus.telemetry.ai;

import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.inject.Singleton;

@Singleton
@ModelName("scorer")
public class GrokScorerChatModelCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {

    @Override
    public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
        GrokChatModelCustomizer.apply(builder);
    }
}
