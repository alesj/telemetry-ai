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
    TelemetryAiService telemetry;

    @Inject
    DevMcpAiService devmcp;

    @GET
    @Path("/analyze/{n}")
    @Produces(MediaType.APPLICATION_JSON)
    public AnalysisResult analyze(@PathParam("n") int n,
                                  @QueryParam("outputType") @DefaultValue("html") String outputType,
                                  @QueryParam("createDashboard") @DefaultValue("false") boolean createDashboard,
                                  @QueryParam("examineSource") @DefaultValue("false") boolean examineSource) {
        log.info("Analyzing n={} outputType={} createDashboard={} examineSource={}", n, outputType, createDashboard, examineSource);

        String analysis = telemetry.analyze(n, outputType);

        String sources = null;
        if (examineSource) {
            sources = devmcp.examineSource(analysis);
        }

        String dashboard = null;
        if (createDashboard) {
            dashboard = devmcp.createDashboard(analysis);
        }

        return new AnalysisResult(analysis, sources, dashboard);
    }
}
