package io.quarkus.telemetry.ai;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PlainTools {

    @OutputGuardrails(LogGuardrail.class)
    @Tool("Extract root span start time from trace JSON")
    public String extractRootSpanStartTime(String traceJson) {
        System.out.println("Extracting trace datetime ...");
        System.out.println("---");
        System.out.println(traceJson);
        System.out.println("---");
        String datetime = StripFunctions.extractRootSpanStartTime(traceJson);
        System.out.println("Trace datetime = " + datetime);
        return datetime;
    }
}
