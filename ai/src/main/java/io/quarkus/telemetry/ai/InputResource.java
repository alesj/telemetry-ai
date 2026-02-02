package io.quarkus.telemetry.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.telemetry.common.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
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

    @POST
    @Path("/analyze")
    public void analyze(Context context) throws IOException {
        String cs = mapper.writeValueAsString(context);
        log.info("Application behavior: " + service.analyze(cs));
    }
}
