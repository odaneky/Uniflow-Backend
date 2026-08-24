package com.university.lms.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
 *
 * <p>Claiming and per-row processing are deliberately separate transactions. Claiming a whole batch
 * and then processing it inside that same still-open transaction held the {@code FOR UPDATE} locks
 * for the entire batch's handler-execution time, and — the sharper defect — meant one row's
 * failure could mark the whole enclosing transaction rollback-only, silently discarding every row
 * already processed in the batch and losing the very failure record meant to explain why. Claiming
 * now commits and releases its locks immediately; each row is then attempted, and any failure
 * recorded, in {@link OutboxRowProcessor}'s own independent transactions.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final DomainOutboxRepository repository;
    private final OutboxRowProcessor rowProcessor;
    private final String instanceId;

    @Value("${lms.outbox.batch-size:25}")
    private int batchSize;

    public OutboxDispatcher(
            DomainOutboxRepository repository,
            OutboxRowProcessor rowProcessor,
            @Value("${lms.instance-id:#{T(java.util.UUID).randomUUID().toString()}}") String instanceId) {
        this.repository = repository;
        this.rowProcessor = rowProcessor;
        this.instanceId = instanceId;
    }

    @Scheduled(fixedDelayString = "${lms.outbox.poll-interval-ms:2000}")
    public void poll() {
        Instant now = Instant.now();
        for (UUID rowId : claimBatch(now)) {
            processRow(rowId, now);
        }
    }

    @Transactional
    List<UUID> claimBatch(Instant now) {
        List<DomainOutbox> batch = repository.claimBatch(now, batchSize);
        for (DomainOutbox row : batch) {
            row.markProcessing(instanceId, now);
        }
        return batch.stream().map(DomainOutbox::getId).toList();
    }

    private void processRow(UUID rowId, Instant now) {
        try {
            rowProcessor.attempt(rowId, now);
        } catch (Exception ex) {
            try {
                rowProcessor.recordFailure(rowId, ex.getMessage(), now);
            } catch (RuntimeException recordEx) {
                // The row itself is the anomaly here (see requireRow), not the batch. Losing this
                // one row's failure record must not stop the rest of the batch from being tried.
                log.error("Could not record outbox failure for row {}: {}", rowId, recordEx.getMessage());
            }
        }
    }

    /** Visible for integration tests. */
    public void drainOnce() {
        poll();
    }
}
