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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes exactly one outbox row per call, each in its own independent transaction.
 *
 * <p>Split out of {@link OutboxDispatcher} on purpose: {@code REQUIRES_NEW} only creates a genuinely
 * new physical transaction when the call arrives through the Spring proxy, and a method calling
 * another method on {@code this} never goes through it. Keeping this on a separate bean is what
 * makes the propagation real rather than a no-op.
 *
 * <p>{@code attempt} and {@code recordFailure} are two <em>separate</em> transactions, not one that
 * catches its own exception. A flush failure inside a handler (a constraint violation, most often)
 * marks the current Hibernate session rollback-only; trying to record the failure in that same,
 * already-doomed transaction either silently loses the write or throws
 * {@code UnexpectedRollbackException} when the transaction manager tries to commit it. Recording the
 * failure has to happen in a transaction that never touched the thing that failed.
 */
@Component
class OutboxRowProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxRowProcessor.class);
    private static final int MAX_ATTEMPTS = 5;

    private final DomainOutboxRepository repository;
    private final Map<String, OutboxEventHandler> handlers;
    private final CommsMetrics commsMetrics;

    OutboxRowProcessor(DomainOutboxRepository repository, List<OutboxEventHandler> handlerList, CommsMetrics commsMetrics) {
        this.repository = repository;
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(OutboxEventHandler::eventType, Function.identity(), (a, b) -> a));
        this.commsMetrics = commsMetrics;
    }

    /**
     * Runs the handler for one row and records success, entirely within one fresh transaction. If
     * the handler throws, this transaction rolls back in full — including any partial writes the
     * handler already made — and the exception propagates to the caller, which is expected to call
     * {@link #recordFailure} in a transaction of its own.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void attempt(UUID rowId, Instant now) throws Exception {
        DomainOutbox row = requireRow(rowId);
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
        } finally {
            commsMetrics.recordOutboxDuration(sample, row.getEventType());
        }
    }

    /** Always succeeds against a row {@link #attempt} never wrote to — a fresh read, a fresh write. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordFailure(UUID rowId, String errorMessage, Instant now) {
        DomainOutbox row = requireRow(rowId);
        log.warn("Outbox processing failed for row {}: {}", rowId, errorMessage);
        String outcome = row.getAttemptCount() + 1 >= MAX_ATTEMPTS ? "dead_letter" : "retry";
        commsMetrics.outboxProcessed(row.getEventType(), outcome);
        if (row.getAttemptCount() + 1 >= MAX_ATTEMPTS) {
            row.markFailed(truncate(errorMessage), Instant.MAX);
        } else {
            row.markFailed(truncate(errorMessage), backoff(now, row.getAttemptCount() + 1));
        }
        repository.save(row);
    }

    private DomainOutbox requireRow(UUID rowId) {
        return repository
                .findById(rowId)
                .orElseThrow(() -> new IllegalStateException("Outbox row " + rowId + " vanished mid-processing"));
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
