package io.quarkus.telemetry.app;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/")
public class PokeResource {

    @GET
    @Path("/poke")
    @Produces(MediaType.TEXT_PLAIN)
    public String poke(@QueryParam("value") Integer value) {
        if (value == null) {
            return "poke received (no value)";
        }
        return "poke received: " + value;
    }
}
