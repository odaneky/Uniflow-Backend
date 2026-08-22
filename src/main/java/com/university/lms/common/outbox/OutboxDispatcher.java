package com.university.lms.common.outbox;

import com.university.lms.common.telemetry.CommsMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims outbox rows with {@code FOR UPDATE SKIP LOCKED} and dispatches to registered handlers.
 *
 * <p>Safe for multiple application instances — two dispatchers cannot process the same row.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int MAX_ATTEMPTS = 5;

    private final DomainOutboxRepository repository;
    private final Map<String, OutboxEventHandler> handlers;
    private final CommsMetrics commsMetrics;
    private final String instanceId;

    @Value("${lms.outbox.batch-size:25}")
    private int batchSize;

    public OutboxDispatcher(
            DomainOutboxRepository repository,
            List<OutboxEventHandler> handlerList,
            CommsMetrics commsMetrics,
            @Value("${lms.instance-id:#{T(java.util.UUID).randomUUID().toString()}}") String instanceId) {
        this.repository = repository;
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(OutboxEventHandler::eventType, Function.identity(), (a, b) -> a));
        this.commsMetrics = commsMetrics;
        this.instanceId = instanceId;
    }

    @Scheduled(fixedDelayString = "${lms.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        Instant now = Instant.now();
        List<DomainOutbox> batch = repository.claimBatch(now, batchSize);
        for (DomainOutbox row : batch) {
            row.markProcessing(instanceId, now);
            repository.save(row);
            processRow(row, now);
        }
    }

    private void processRow(DomainOutbox row, Instant now) {
        OutboxEventHandler handler = handlers.get(row.getEventType());
        if (handler == null) {
            row.markFailed("No handler for event type " + row.getEventType(), now);
            repository.save(row);
            commsMetrics.outboxProcessed(row.getEventType(), "no_handler");
            log.warn("Outbox row {} has no handler for {}", row.getId(), row.getEventType());
            return;
        }
        Timer.Sample sample = commsMetrics.startOutboxTimer();
        try {
            handler.handle(row);
            row.markProcessed(now);
            repository.save(row);
            commsMetrics.outboxProcessed(row.getEventType(), "success");
        } catch (Exception ex) {
            log.warn("Outbox processing failed for row {}: {}", row.getId(), ex.getMessage());
            String outcome = row.getAttemptCount() + 1 >= MAX_ATTEMPTS ? "dead_letter" : "retry";
            commsMetrics.outboxProcessed(row.getEventType(), outcome);
            if (row.getAttemptCount() + 1 >= MAX_ATTEMPTS) {
                row.markFailed(truncate(ex.getMessage()), Instant.MAX);
            } else {
                row.markFailed(truncate(ex.getMessage()), backoff(now, row.getAttemptCount() + 1));
            }
            repository.save(row);
        } finally {
            commsMetrics.recordOutboxDuration(sample, row.getEventType());
        }
    }

    /** Visible for integration tests. */
    @Transactional
    public void drainOnce() {
        poll();
    }

    private static Instant backoff(Instant from, int attempt) {
        long seconds = (long) Math.pow(2, attempt);
        return from.plusSeconds(Math.min(seconds, 300));
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
