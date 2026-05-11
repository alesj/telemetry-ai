package io.quarkus.telemetry.ai;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Path("/")
public class InputResource {
    private static final Logger log = LoggerFactory.getLogger(InputResource.class);

    @Inject
    AiService service;

    @GET
    @Path("/analyze/{n}")
    public void analyze(@PathParam("n") int n) {
        log.info("Application behavior: \n" + service.analyze(n));
    }
}
