package com.university.lms.common.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reproduces the outbox dispatcher's poisoned-transaction bug: runs a statement Postgres itself
 * refuses (division by zero), which aborts the current transaction at the database — not merely
 * throws a Java exception before any database work. Every later statement on that same
 * transaction, including one written by application code trying to record what happened, then
 * fails with "current transaction is aborted" until the transaction ends. That is the failure mode
 * {@link OutboxRowProcessor} exists to survive.
 *
 * <p>Permanently registered, matching how {@code AcademicFixtures} is test support rather than a
 * per-test double: {@code EVENT_TYPE} is never enqueued by production code, so this is inert outside
 * a test that deliberately enqueues one.
 */
@Component
class PoisonOutboxHandler implements OutboxEventHandler {

    static final String EVENT_TYPE = "IntegrationTestPoison";

    private final JdbcTemplate jdbcTemplate;

    PoisonOutboxHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(DomainOutbox row) {
        jdbcTemplate.execute("SELECT 1/0");
    }
}
