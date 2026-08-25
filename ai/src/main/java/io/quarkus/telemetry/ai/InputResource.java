package io.quarkus.telemetry.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static io.quarkus.telemetry.ai.DashboardUtils.sanitizeDashboardJson;

@ApplicationScoped
@Path("/")
public class InputResource {
    private static final Logger log = LoggerFactory.getLogger(InputResource.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    TelemetryAiService telemetry;

    @Inject
    DevMcpAiService devmcp;

    @Inject
    AnalysisMetrics metrics;

    @Inject
    DevMcpToolProviderSupplier devMcpTools;

    @GET
    @Path("/analyze/{n}")
    @Produces(MediaType.APPLICATION_JSON)
    public AnalysisResult analyze(@PathParam("n") int n,
                                  @QueryParam("outputType") @DefaultValue("html") String outputType,
                                  @QueryParam("createDashboard") @DefaultValue("false") boolean createDashboard,
                                  @QueryParam("examineSource") @DefaultValue("false") boolean examineSource) {
        log.info("Analyzing n={} outputType={} createDashboard={} examineSource={}", n, outputType, createDashboard, examineSource);

        metrics.reset();
        long startTime = System.nanoTime();

        String analysis = telemetry.analyze(n, outputType);

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        String sources = null;
        if (examineSource) {
            sources = devmcp.examineSource(analysis);
        }

        String dashboard = null;
        if (createDashboard) {
            devMcpTools.resetSaveTracking();
            dashboard = sanitizeDashboardJson(mapper, devmcp.createDashboard(analysis));
            devMcpTools.saveDashboardToUnsaved(dashboard);
        }

        AnalysisResult.PerfInfo perf = metrics.snapshot(durationMs);
        log.info("Analysis completed in {}ms, {} LLM calls, {} input tokens, {} output tokens",
                perf.durationMs(), perf.llmCalls(), perf.inputTokens(), perf.outputTokens());

        return new AnalysisResult(analysis, sources, dashboard, perf);
    }
}
