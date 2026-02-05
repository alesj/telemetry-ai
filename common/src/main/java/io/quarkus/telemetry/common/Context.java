package io.quarkus.telemetry.common;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class Context {
    private List<Data> data;

    @JsonProperty
    public List<Data> getData() {
        return data;
    }

    public void setData(List<Data> data) {
        this.data = data;
    }

    public static class Data {
        @JsonProperty
        private List<Map<String, Object>> logging;

        @JsonProperty
        private Map<String, Object> tracing;

        @JsonProperty
        private List<Map<String, Object>> metrics;

        @JsonProperty
        private List<Map<String, Object>> profiling;

        public List<Map<String, Object>> getLogging() {
            return logging;
        }

        public void setLogging(List<Map<String, Object>> logging) {
            this.logging = logging;
        }

        public Map<String, Object> getTracing() {
            return tracing;
        }

        public void setTracing(Map<String, Object> tracing) {
            this.tracing = tracing;
        }

        public List<Map<String, Object>> getMetrics() {
            return metrics;
        }

        public void setMetrics(List<Map<String, Object>> metrics) {
            this.metrics = metrics;
        }

        public List<Map<String, Object>> getProfiling() {
            return profiling;
        }

        public void setProfiling(List<Map<String, Object>> profiling) {
            this.profiling = profiling;
        }
    }
}
