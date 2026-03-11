package io.quarkus.telemetry.ai.lgtm;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/")
@RegisterRestClient(configKey = "lgtm")
@Produces(MediaType.APPLICATION_JSON)
public interface LgtmClient {
    @GET
    @Path("/loki/api/v1/query_range")
    String logQueryRange(
            @QueryParam("query") String query,
            @QueryParam("start") Long start,
            @QueryParam("end") Long end,
            @QueryParam("limit") Integer limit
    );

    @GET
    @Path("/api/traces/{traceId}")
    String getTrace(@PathParam("traceId") String traceId);


}
