package io.quarkus.telemetry.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(toolProviderSupplier = DevMcpToolProviderSupplier.class)
public interface DevMcpAiService {
    @SystemMessage(
            """
                    Examine {{analysis}}, create a complete Grafana dashboard JSON that visualizes the key findings.
                    The dashboard should:
                    - Use the "prometheus" datasource UID
                    - Include panels for every metric that was flagged as concerning or relevant in the analysis
                    - Organize panels into rows by category (e.g., "JVM Memory", "CPU", "HTTP Server", "Worker Pool")
                    - Each panel should have:
                      - A meaningful title matching the finding (e.g., "JVM Memory Usage vs Max")
                      - The exact PromQL expression used (e.g., jvm_memory_used_bytes, process_cpu_usage)
                      - Appropriate visualization type: timeseries for trends, gauge for current values, stat for single metrics
                      - Thresholds matching the critical thresholds from the analysis (e.g., red at 90% memory)
                    - Include a dashboard title like "Telemetry Analysis Dashboard" and appropriate tags ["generated", "telemetry-ai"]
                    - The JSON must be valid and importable into Grafana as-is

                    MANDATORY: You MUST save the dashboard JSON file to EVERY application workspace.
                    There are multiple workspace tool sets, one per application, each prefixed with a different
                    "dev-mcp-<port>_devui-workspace_" prefix (e.g. dev-mcp-8081, dev-mcp-8082, etc.).
                    You MUST repeat the save step for EACH prefix — every application gets the dashboard.

                    For each workspace tool set, call the "saveWorkspaceItemContent" tool for that prefix with:
                    - path: "src/main/resources/META-INF/grafana/grafana-dashboard-generated-dashboard.json"
                    - content: the complete dashboard JSON string

                    Do NOT skip any workspace. Every application MUST receive the dashboard file.

                    After writing to all workspaces, return the dashboard JSON as a result.
                    """
    )
    String createDashboard(@V("analysis") String analysis);

    @SystemMessage(
            """
                    You are a code analyst. Given a telemetry analysis report, examine the application source code
                    to find the exact code responsible for each issue identified in the analysis.

                    ANALYSIS REPORT:
                    {{analysis}}

                    INSTRUCTIONS:
                    1. Parse the analysis above and extract every key finding: errors, latency spikes, resource pressure,
                       abnormal span durations, HTTP error codes, Thread.sleep calls, memory allocations, CPU burns, etc.

                    2. Use the workspace tools to explore ALL application source code.
                       There are multiple workspace tool sets, one per application, each prefixed with a different
                       "dev-mcp-<port>_devui-workspace_" prefix (e.g. dev-mcp-8081, dev-mcp-8082, etc.).
                       You MUST examine sources from EVERY workspace, not just the first one.

                       For each workspace tool set:
                       - Call the "getWorkspaceItems" tool for that prefix to list all source files
                       - Call the "getWorkspaceItemContent" tool for that prefix to read relevant source files

                    3. For EACH finding in the analysis, locate the EXACT source code responsible:
                       - Match HTTP endpoints from traces to their handler methods
                       - Match error codes to the code that produces them
                       - Match latency issues to blocking or slow code paths
                       - Match resource pressure to memory allocations, CPU-intensive loops, or leak patterns
                       - Trace the call chain between services if applicable

                       IMPORTANT: ONLY report on code that DIRECTLY correlates to a specific finding
                       in the analysis above. Do NOT report on code that was not exercised or observed
                       in the telemetry data. If an endpoint or method does not appear in any trace,
                       log, or metric from the analysis, do NOT include it. Every finding you report
                       MUST have concrete telemetry evidence from the analysis.

                    4. For each correlated finding, produce:
                       - **File**: full path to the source file
                       - **Class.method**: the class and method name
                       - **Line**: approximate line number
                       - **Code snippet**: the relevant lines of code
                       - **Telemetry evidence**: what the analysis reported (span duration, error code, metric value)
                       - **Explanation**: why this code causes the observed behavior
                       - **Suggested fix**: a concrete code change or an explanation of why the code is intentional

                    5. Output a structured report with these sections:

                       ## Source Code Examination

                       ### Finding 1: [short title matching the analysis finding]
                       - **Source**: `path/to/File.java` — `ClassName.methodName()` (line N)
                       - **Code**:
                         ```
                         // relevant code snippet
                         ```
                       - **Telemetry evidence**: [what the analysis found]
                       - **Root cause**: [why this code causes the issue]
                       - **Recommendation**: [fix or justification]

                       [Repeat for each finding]

                       ### Architecture Overview
                       Summarize the application structure and how the source code
                       maps to the distributed trace structure observed in the analysis.

                       ### Summary
                       - Total findings correlated to source: N of M
                       - Critical code issues: [list]
                       - Intentional behavior: [list]
                    """
    )
    String examineSource(@V("analysis") String analysis);
}
