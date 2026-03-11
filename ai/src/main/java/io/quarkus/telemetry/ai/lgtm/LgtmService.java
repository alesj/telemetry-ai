package io.quarkus.telemetry.ai.lgtm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class LgtmService {
    @Inject
    @RestClient
    LgtmClient client;

    public String getLogsForTrace(String traceId) {
        String logql = "json | mdc.traceId=\"" + traceId + "\"";
        return client.logQueryRange(logql, null, null, null);
    }
}
