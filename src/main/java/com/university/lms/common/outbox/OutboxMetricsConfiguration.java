package com.university.lms.common.outbox;

import com.university.lms.common.telemetry.CommsMetrics;
import java.util.List;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxMetricsConfiguration {

    public OutboxMetricsConfiguration(DomainOutboxRepository repository, CommsMetrics commsMetrics) {
        commsMetrics.registerOutboxPendingGauge(() -> repository.countByStatusIn(List.of(OutboxStatus.PENDING, OutboxStatus.FAILED)));
    }
}
