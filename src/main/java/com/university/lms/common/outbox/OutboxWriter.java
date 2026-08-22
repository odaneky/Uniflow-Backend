package com.university.lms.common.outbox;

import org.springframework.stereotype.Component;

/** Inserts outbox rows in the caller's transaction. */
@Component
public class OutboxWriter {

    private final DomainOutboxRepository repository;

    public OutboxWriter(DomainOutboxRepository repository) {
        this.repository = repository;
    }

    public DomainOutbox enqueue(
            String aggregateType,
            java.util.UUID aggregateId,
            String eventType,
            String payloadJson,
            String idempotencyKey) {
        return repository.save(
                new DomainOutbox(aggregateType, aggregateId, eventType, payloadJson, idempotencyKey));
    }
}
