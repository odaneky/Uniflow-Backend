package com.university.lms.common.telemetry;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Low-cardinality comms and outbox metrics — no user identifiers in tags. */
@Component
public class CommsMetrics {

    private final MeterRegistry meterRegistry;

    public CommsMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void outboxProcessed(String eventType, String outcome) {
        meterRegistry
                .counter("uniflow.outbox.processed", "event_type", eventType, "outcome", outcome)
                .increment();
    }

    public Timer.Sample startOutboxTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordOutboxDuration(Timer.Sample sample, String eventType) {
        sample.stop(Timer.builder("uniflow.outbox.dispatch.duration")
                .tag("event_type", eventType)
                .register(meterRegistry));
    }

    public void rateLimitHit(String bucket) {
        meterRegistry.counter("uniflow.comms.rate_limit", "bucket", bucket).increment();
    }

    public void registerOutboxPendingGauge(Supplier<Number> supplier) {
        Gauge.builder("uniflow.outbox.pending", supplier).register(meterRegistry);
    }
}
