package com.university.lms.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Low-cardinality domain counters. Tags are outcomes, never student or user identifiers — those
 * belong on the audit trail, not on a time series.
 */
@Component
public class UniFlowMetrics {

    private final MeterRegistry meterRegistry;

    public UniFlowMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void enrolment(String outcome) {
        meterRegistry.counter("uniflow.enrolment", "outcome", outcome).increment();
    }

    public void occurrence(String outcome) {
        meterRegistry.counter("uniflow.occurrence", "outcome", outcome).increment();
    }

    public void grade(String outcome) {
        meterRegistry.counter("uniflow.grade", "outcome", outcome).increment();
    }
}
