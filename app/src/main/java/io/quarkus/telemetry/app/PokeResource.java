package io.quarkus.telemetry.app;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.util.Random;

@ApplicationScoped
@Path("/")
public class PokeResource {
    private static final Logger log = Logger.getLogger(PokeResource.class);

    @Inject
    MeterRegistry registry;

    Random random = new SecureRandom();
    Integer[] arr = new Integer[1];

    @PostConstruct
    public void start() {
        String key = System.getProperty("tag-key", "app");
        Gauge.builder("poke_value", arr, a -> arr[0])
                .baseUnit("int")
                .description("Poke value")
                .tag(key, "poke")
                .register(registry);
    }

    static boolean inRange(int from, int till, Integer value) {
        return value != null && from <= value && value < till;
    }

    @GET
    @Path("/poke")
    @Produces(MediaType.TEXT_PLAIN)
    public Response poke(@QueryParam("value") Integer value) {
        log.info("Poking ... " + value);
        arr[0] = value;
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
        if (!inRange(200, 600, value)) {
            copy = 200; // plain ok
        }
        return Response.status(copy).entity("Poked with " + value).build();
    }
}
