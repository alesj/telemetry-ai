package io.quarkus.telemetry.proxy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
@Path("/")
public class ProxyResource {
    private static final Logger log = Logger.getLogger(ProxyResource.class);

    @Inject
    @RestClient
    ProxyClient proxy;

    @GET
    @Path("/poke")
    public void pokeFwd(@QueryParam("value") Integer value) {
        log.info("Proxying pokeFwd ... " + value);
        try (Response response = proxy.pokeFwd(value)) {
            log.info("Proxy received: " + response.readEntity(String.class));
        }
    }

    @GET
    @Path("/chaos")
    public void chaosFwd(@QueryParam("type") String type, @QueryParam("intensity") Integer intensity) {
        log.info("Proxying chaos ... type=" + type + " intensity=" + intensity);
        try (Response response = proxy.chaosFwd(type, intensity)) {
            log.info("Chaos received: " + response.readEntity(String.class));
        }
    }
}
