package io.quarkus.telemetry.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInvocationContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.function.Predicate;

@ApplicationScoped
public class JsonValidator implements ToolInputGuardrail {

    @Inject
    ObjectMapper mapper;

    @Inject
    Predicate<InvocationContext> predicate;

    @Override
    public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
        ToolInvocationContext tic = request.invocationContext();
        InvocationContext ic = tic.context();
        String raw = request.arguments();

        if (predicate.test(ic)) {
            try {
                if (raw.startsWith("\"") && raw.endsWith("\"")) {
                    raw = mapper.readValue(raw, String.class);
                }

            } catch (JsonProcessingException e) {
                return ToolInputGuardrailResult.failure("Invalid JSON");
            }
        }

        return ToolInputGuardrailResult.successWith(
                ToolExecutionRequest.builder()
                        .id(request.executionRequest().id())
                        .name(request.executionRequest().name())
                        .arguments(raw)
                        .build()
        );
    }
}