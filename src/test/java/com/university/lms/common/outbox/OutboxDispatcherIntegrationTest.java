package com.university.lms.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A row whose handler aborts the database transaction used to take its whole batch down with it:
 * {@code poll()} wrapped claiming and every row's processing in one transaction, so a Postgres-level
 * abort from one handler marked that shared transaction rollback-only, and the failure-recording
 * write for the poisoned row — along with every already-succeeded row in the same batch — was
 * silently discarded when {@code poll()} returned. The poisoned row went back to {@code PENDING}
 * with no error and no attempt recorded, so the next poll claimed it again, forever.
 */
class OutboxDispatcherIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OutboxWriter outboxWriter;

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @Autowired
    private DomainOutboxRepository outboxRepository;

    @Test
    @DisplayName("a poisoned row in a batch is marked FAILED, not silently reverted to PENDING")
    void poisonedRowIsRecordedAsFailed() {
        DomainOutbox poison = outboxWriter.enqueue(
                "Test", java.util.UUID.randomUUID(), PoisonOutboxHandler.EVENT_TYPE, "{}", "poison:" + java.util.UUID.randomUUID());

        outboxDispatcher.drainOnce();

        DomainOutbox reloaded = outboxRepository.findById(poison.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        assertThat(reloaded.getLastError()).isNotBlank();
        assertThat(reloaded.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("a healthy row is unaffected by a poisoned row in the same batch")
    void healthyRowInSameBatchStillSucceeds() {
        // Older created_at first, so both are claimed together by the same ORDER BY created_at batch.
        DomainOutbox poison = outboxWriter.enqueue(
                "Test", java.util.UUID.randomUUID(), PoisonOutboxHandler.EVENT_TYPE, "{}", "poison:" + java.util.UUID.randomUUID());
        DomainOutbox healthy = outboxWriter.enqueue(
                "Test",
                java.util.UUID.randomUUID(),
                NoOpSuccessOutboxHandler.EVENT_TYPE,
                "{}",
                "healthy:" + java.util.UUID.randomUUID());

        outboxDispatcher.drainOnce();

        DomainOutbox reloadedHealthy = outboxRepository.findById(healthy.getId()).orElseThrow();
        assertThat(reloadedHealthy.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(reloadedHealthy.getProcessedAt()).isNotNull();

        DomainOutbox reloadedPoison = outboxRepository.findById(poison.getId()).orElseThrow();
        assertThat(reloadedPoison.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }
}
