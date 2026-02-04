package io.quarkus.telemetry.proxy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Path("/")
public class ProxyResource {
    private static final Logger log = LoggerFactory.getLogger(ProxyResource.class);

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
}
