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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@ApplicationScoped
@Path("/")
public class PokeResource {
    private static final Logger log = Logger.getLogger(PokeResource.class);

    @Inject
    MeterRegistry registry;

    Random random = new SecureRandom();
    Integer[] arr = new Integer[1];
    final List<byte[]> leaks = new ArrayList<>();

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

    @GET
    @Path("/chaos")
    @Produces(MediaType.TEXT_PLAIN)
    public Response chaos(@QueryParam("type") String type, @QueryParam("intensity") Integer intensity) {
        log.info("Chaos invoked: type=" + type + " intensity=" + intensity);
        if (type == null) {
            return Response.status(400).entity("Missing 'type' parameter").build();
        }
        return switch (type) {
            case "delay" -> chaosDelay(intensity != null ? intensity : 3000);
            case "memory" -> chaosMemory(intensity != null ? intensity : 50);
            case "cpu" -> chaosCpu(intensity != null ? intensity : 2000);
            case "leak" -> chaosLeak(intensity != null ? intensity : 50);
            case "error" -> chaosError();
            default -> Response.status(400).entity("Unknown chaos type: " + type).build();
        };
    }

    private Response chaosDelay(int millis) {
        log.warn("Chaos delay: sleeping " + millis + "ms");
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Response.ok("Delayed " + millis + "ms").build();
    }

    private Response chaosMemory(int megabytes) {
        log.warn("Chaos memory: allocating " + megabytes + "MB");
        byte[] blob = new byte[megabytes * 1024 * 1024];
        for (int i = 0; i < blob.length; i += 4096) {
            blob[i] = (byte) i;
        }
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        log.warn("Chaos memory: heap used = " + (used / 1024 / 1024) + "MB");
        return Response.ok("Allocated " + megabytes + "MB, heap used " + (used / 1024 / 1024) + "MB").build();
    }

    private Response chaosCpu(int millis) {
        log.warn("Chaos CPU: burning for " + millis + "ms");
        long end = System.currentTimeMillis() + millis;
        double sink = 0;
        while (System.currentTimeMillis() < end) {
            sink += Math.sin(random.nextDouble()) * Math.cos(random.nextDouble());
        }
        return Response.ok("CPU burn " + millis + "ms (sink=" + sink + ")").build();
    }

    private Response chaosLeak(int megabytes) {
        log.warn("Chaos leak: leaking " + megabytes + "MB (total chunks: " + (leaks.size() + 1) + ")");
        byte[] blob = new byte[megabytes * 1024 * 1024];
        for (int i = 0; i < blob.length; i += 4096) {
            blob[i] = (byte) i;
        }
        leaks.add(blob);
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        log.warn("Chaos leak: total leaked " + (leaks.size() * megabytes) + "MB, heap used " + (used / 1024 / 1024) + "MB");
        return Response.ok("Leaked " + megabytes + "MB, total chunks " + leaks.size() + ", heap used " + (used / 1024 / 1024) + "MB").build();
    }

    private Response chaosError() {
        int[] codes = {500, 502, 503};
        int code = codes[random.nextInt(codes.length)];
        log.error("Chaos error: throwing " + code);
        Response.Status status = Response.Status.fromStatusCode(code);
        throw new WebApplicationException(
                "Chaos error: " + code,
                Response.status(status).entity("Chaos error " + code).build()
        );
    }
}
