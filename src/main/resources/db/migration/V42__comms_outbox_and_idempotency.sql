-- Transactional outbox for durable async side effects (notifications, email, SSE).
CREATE TABLE domain_outbox (
    id               UUID PRIMARY KEY,
    aggregate_type   VARCHAR(50)  NOT NULL,
    aggregate_id     UUID         NOT NULL,
    event_type       VARCHAR(50)  NOT NULL,
    payload          JSONB        NOT NULL,
    idempotency_key  VARCHAR(200) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count    INT          NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_at        TIMESTAMPTZ,
    locked_by        VARCHAR(100),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at     TIMESTAMPTZ,
    last_error       VARCHAR(500),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100)
);

CREATE UNIQUE INDEX uk_domain_outbox_idempotency ON domain_outbox (idempotency_key);
CREATE INDEX idx_domain_outbox_claimable ON domain_outbox (next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

-- Message idempotency for safe client retries.
ALTER TABLE messages ADD COLUMN idempotency_key VARCHAR(200);
ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE messages ADD COLUMN deleted_by_user_id UUID;

CREATE UNIQUE INDEX uk_messages_conversation_idempotency
    ON messages (conversation_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_messages_conversation_sent_id ON messages (conversation_id, sent_at DESC, id DESC);

-- Notification deep links and dedupe (keep status enum during transition).
ALTER TABLE notifications ADD COLUMN source_type VARCHAR(30);
ALTER TABLE notifications ADD COLUMN source_id UUID;
ALTER TABLE notifications ADD COLUMN action_url VARCHAR(500);
ALTER TABLE notifications ADD COLUMN priority SMALLINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_notifications_recipient_source
    ON notifications (recipient_user_id, source_type, source_id, channel)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL;
