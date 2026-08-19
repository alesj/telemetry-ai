package io.quarkus.telemetry.ai;

public record AnalysisResult(
        String analysis,
        String examinedSources,
        String dashboard
) {}
