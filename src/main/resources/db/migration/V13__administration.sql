-- Append-only audit history of consequential actions.

CREATE TABLE audit_events (
    id            UUID         PRIMARY KEY,
    actor_user_id UUID,
    action        VARCHAR(100) NOT NULL,
    entity_type   VARCHAR(100) NOT NULL,
    entity_id     UUID,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    details       VARCHAR(4000),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    -- Deliberately ON DELETE SET NULL rather than CASCADE: removing a user must not erase the
    -- history of what that user did.
    CONSTRAINT fk_audit_events_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_audit_events_occurred ON audit_events (occurred_at);
CREATE INDEX idx_audit_events_entity   ON audit_events (entity_type, entity_id);
CREATE INDEX idx_audit_events_actor    ON audit_events (actor_user_id);
