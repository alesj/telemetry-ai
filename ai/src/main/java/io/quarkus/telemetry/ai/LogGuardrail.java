package io.quarkus.telemetry.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LogGuardrail implements OutputGuardrail {
    private static final Logger log = Logger.getLogger(LogGuardrail.class);

    @Override
    public OutputGuardrailResult validate(AiMessage response) {
        log.info(">> " + response.toString());
        return success();
    }
}
