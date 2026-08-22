package com.university.lms.common.telemetry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Production must name a collector. Falling back to localhost would either dump traces onto a
 * developer's machine or drop them on the floor while reporting a healthy process.
 */
@Component
@Profile("prod")
public class OtlpProductionGuard {

    public OtlpProductionGuard(
            @Value("${management.otlp.tracing.endpoint:}") String tracesEndpoint,
            @Value("${management.otlp.metrics.export.url:}") String metricsEndpoint) {
        if (!StringUtils.hasText(tracesEndpoint) || looksLocal(tracesEndpoint)) {
            throw new IllegalStateException(
                    "Production requires OTEL_EXPORTER_OTLP_TRACES_ENDPOINT pointing at a collector, not localhost");
        }
        if (!StringUtils.hasText(metricsEndpoint) || looksLocal(metricsEndpoint)) {
            throw new IllegalStateException(
                    "Production requires OTEL_EXPORTER_OTLP_METRICS_ENDPOINT pointing at a collector, not localhost");
        }
    }

    private static boolean looksLocal(String endpoint) {
        String lower = endpoint.toLowerCase();
        return lower.contains("localhost") || lower.contains("127.0.0.1");
    }
}
