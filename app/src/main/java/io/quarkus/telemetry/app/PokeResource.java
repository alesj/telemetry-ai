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
            case "exception" -> chaosException();
            case "threadpool" -> chaosThreadPool(intensity != null ? intensity : 5000);
            case "gc" -> chaosGc(intensity != null ? intensity : 200);
            case "contention" -> chaosContention(intensity != null ? intensity : 3000);
            case "intermittent" -> chaosIntermittent(intensity != null ? intensity : 40);
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

    private Response chaosException() {
        log.error("Chaos exception: throwing unhandled RuntimeException");
        throw new RuntimeException("Chaos unhandled exception: simulated application failure");
    }

    private Response chaosThreadPool(int holdMillis) {
        int threadCount = 10;
        log.warn("Chaos threadpool: blocking " + threadCount + " threads for " + holdMillis + "ms");
        var latch = new java.util.concurrent.CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            Thread.startVirtualThread(() -> {
                log.warn("Chaos threadpool: thread " + idx + " blocked");
                try {
                    Thread.sleep(holdMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.warn("Chaos threadpool: all " + threadCount + " threads released");
        return Response.ok("Blocked " + threadCount + " threads for " + holdMillis + "ms").build();
    }

    private final Object contentionLock = new Object();

    private Response chaosContention(int holdMillis) {
        int threadCount = 10;
        int holdPerThread = holdMillis / threadCount;
        log.warn("Chaos lock contention: " + threadCount + " threads competing for single synchronized lock, each holding " + holdPerThread + "ms, total serialized wait " + holdMillis + "ms");
        var latch = new java.util.concurrent.CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            Thread.startVirtualThread(() -> {
                long waitStart = System.currentTimeMillis();
                log.warn("Chaos lock contention: thread " + idx + " BLOCKED waiting to acquire synchronized lock");
                synchronized (contentionLock) {
                    long waited = System.currentTimeMillis() - waitStart;
                    log.warn("Chaos lock contention: thread " + idx + " acquired lock after " + waited + "ms blocked, holding for " + holdPerThread + "ms");
                    try {
                        Thread.sleep(holdPerThread);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.warn("Chaos lock contention: all " + threadCount + " threads completed after serialized execution through single lock");
        return Response.ok("Lock contention: " + threadCount + " threads serialized through one lock, " + holdMillis + "ms total").build();
    }

    private Response chaosIntermittent(int failPercentage) {
        boolean shouldFail = random.nextInt(100) < failPercentage;
        if (shouldFail) {
            int[] codes = {500, 502, 503};
            int code = codes[random.nextInt(codes.length)];
            log.warn("Chaos intermittent: failing with " + code + " (rate=" + failPercentage + "%)");
            throw new WebApplicationException(
                    "Chaos intermittent failure: " + code,
                    Response.status(code).entity("Intermittent failure " + code).build()
            );
        }
        try {
            Thread.sleep(random.nextInt(50));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Chaos intermittent: success (rate=" + failPercentage + "%)");
        return Response.ok("Intermittent OK (fail rate=" + failPercentage + "%)").build();
    }

    private Response chaosGc(int totalMegabytes) {
        log.warn("Chaos GC: churning " + totalMegabytes + "MB in 1MB chunks");
        int chunkSize = 1024 * 1024;
        int chunks = totalMegabytes;
        long allocated = 0;
        for (int i = 0; i < chunks; i++) {
            byte[] chunk = new byte[chunkSize];
            chunk[0] = (byte) i;
            allocated += chunkSize;
        }
        System.gc();
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        log.warn("Chaos GC: churned " + totalMegabytes + "MB, heap used " + (used / 1024 / 1024) + "MB after GC");
        return Response.ok("GC churn " + totalMegabytes + "MB, heap used " + (used / 1024 / 1024) + "MB").build();
    }
}
