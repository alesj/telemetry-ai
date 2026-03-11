package io.quarkus.telemetry.ai.lgtm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TempoQueryRequest {

    public List<TempoQuery> queries;
    public String from;
    public String to;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TempoQuery {

        public String refId;
        public Datasource datasource;
        public String queryType;
        public int limit;
        public String tableType;
        public String metricsQueryType;
        public List<Filter> filters;
        public String query;
        public String serviceMapQuery;
        public int datasourceId;
        public int intervalMs;
        public int maxDataPoints;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Datasource {

        public String type;
        public String uid;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Filter {

        public String id;
        public String operator;
        public String scope;
        public String tag;
    }

}
