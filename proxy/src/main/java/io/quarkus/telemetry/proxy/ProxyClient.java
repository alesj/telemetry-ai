package io.quarkus.telemetry.proxy;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "proxy")
public interface ProxyClient {

    @GET
    @Path("/poke")
    @Produces(MediaType.TEXT_PLAIN)
    Response pokeFwd(@QueryParam("value") Integer value);
}