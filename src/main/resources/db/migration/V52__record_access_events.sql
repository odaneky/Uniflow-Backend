-- FERPA-oriented record access log (separate from operational audit trail).

CREATE TABLE record_access_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id   UUID NOT NULL,
    actor_label     VARCHAR(200),
    student_id      UUID NOT NULL,
    record_type     VARCHAR(60) NOT NULL,
    action          VARCHAR(20) NOT NULL,
    details         VARCHAR(500),
    accessed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(120),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_record_access_student ON record_access_events (student_id, accessed_at DESC);
CREATE INDEX idx_record_access_actor ON record_access_events (actor_user_id, accessed_at DESC);
