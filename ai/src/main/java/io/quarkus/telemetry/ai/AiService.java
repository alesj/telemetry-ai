package io.quarkus.telemetry.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@RegisterAiService
public interface AiService {
    @SystemMessage("""
            You are a professional log, tracing, metrics and profile data analyst. Use the given context to extract application behavior.
            """)
    @UserMessage("""
            Write a concise, well-structured analysis of application's log, traces, metrics and profile data provided in the context.

            Context: {context}
            """)
    String analyze(String context);
}
