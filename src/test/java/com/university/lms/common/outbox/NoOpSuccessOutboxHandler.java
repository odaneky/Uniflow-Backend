package com.university.lms.common.outbox;

import org.springframework.stereotype.Component;

/** A batch-mate with nothing to do — proves it is unaffected by a poisoned row in the same batch. */
@Component
class NoOpSuccessOutboxHandler implements OutboxEventHandler {

    static final String EVENT_TYPE = "IntegrationTestNoOpSuccess";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(DomainOutbox row) {
        // Nothing to do.
    }
}
