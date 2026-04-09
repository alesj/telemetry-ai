package io.quarkus.telemetry.ai;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Path("/")
public class InputResource {
    private static final Logger log = LoggerFactory.getLogger(InputResource.class);

    private static final String USER_MSG =
            """
            Analyze last application traces, logs and metrics.
            """;

    @Inject
    AiService service;

    @GET
    @Path("/analyze")
    public void analyze() {
        log.info("Application behavior: " + service.analyze(USER_MSG));
    }
}
