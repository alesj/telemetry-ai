package io.quarkus.telemetry.ai;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
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
    @Produces(MediaType.TEXT_HTML)
    public String analyze(@PathParam("n") int n,
                          @QueryParam("createDashboard") @DefaultValue("false") boolean createDashboard) {
        String result = String.format("Application behavior (n=%s): \n%s", n, service.analyze(n, "html", createDashboard));
        log.info(result);
        return result;
    }
}
