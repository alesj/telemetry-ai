package io.quarkus.telemetry.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.telemetry.common.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@ApplicationScoped
@Path("/")
public class InputResource {
    private static final Logger log = LoggerFactory.getLogger(InputResource.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    AiService service;

    @GET
    @Path("/analyze")
    @Produces(MediaType.TEXT_PLAIN)
    public void analyze(Context context) throws IOException {
        String cs = mapper.writeValueAsString(context);
        log.info("Application behavior: " + service.analyze(cs));
    }
}
