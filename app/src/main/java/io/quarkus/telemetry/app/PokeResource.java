package io.quarkus.telemetry.app;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/")
public class PokeResource {

    static boolean inRange(int from, int till, Integer value) {
        return value != null && from <= value && value < till;
    }

    @GET
    @Path("/poke")
    @Produces(MediaType.TEXT_PLAIN)
    public Response poke(@QueryParam("value") Integer value) {
        if (inRange(400, 600, value)) {
            Response.Status status = Response.Status.fromStatusCode(value);
            throw new WebApplicationException(
                    "App error: " + status.getStatusCode(),
                    Response.status(status)
                            .entity(status.getReasonPhrase())
                            .build()
            );
        }
        Integer copy = value;
        if (!inRange(100, 600, value)) {
            copy = 200; // plain ok
        }
        return Response.status(copy).entity("Poked with " + value).build();
    }
}
