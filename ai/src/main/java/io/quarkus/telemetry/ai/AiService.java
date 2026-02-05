package io.quarkus.telemetry.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface AiService {
    @SystemMessage("""
            You are a professional log, tracing, metrics and profile data analyst. Use the given context to extract application behavior.
            The context is a JSON content, where each element in the array field named data contains logs, traces, metrics and profiling data
            for a particular single request. Hence if there is multiple data elements, the context covers multiple requests.
            """)
    @UserMessage("""
            Write a concise, well-structured analysis of application's log, traces, metrics and profile data provided in the context.

            Context: {context}
            """)
    String analyze(String context);
}
