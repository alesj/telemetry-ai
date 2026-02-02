package io.quarkus.telemetry.common;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class Context {
    @JsonProperty
    private Map<String, List<Map<String, Object>>> logging;

    @JsonProperty
    private Map<String, List<Map<String, Object>>> tracing;

    @JsonProperty
    private Map<String, List<Map<String, Object>>> metrics;

    @JsonProperty
    private Map<String, List<Map<String, Object>>> profiling;

    public Map<String, List<Map<String, Object>>> getLogging() {
        return logging;
    }

    public void setLogging(Map<String, List<Map<String, Object>>> logging) {
        this.logging = logging;
    }

    public Map<String, List<Map<String, Object>>> getTracing() {
        return tracing;
    }

    public void setTracing(Map<String, List<Map<String, Object>>> tracing) {
        this.tracing = tracing;
    }

    public Map<String, List<Map<String, Object>>> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, List<Map<String, Object>>> metrics) {
        this.metrics = metrics;
    }

    public Map<String, List<Map<String, Object>>> getProfiling() {
        return profiling;
    }

    public void setProfiling(Map<String, List<Map<String, Object>>> profiling) {
        this.profiling = profiling;
    }
}
