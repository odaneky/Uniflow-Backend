-- Service request workflow: payload, assignment, deliverables, history, concurrency.

ALTER TABLE service_requests
    ADD COLUMN payload JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN assigned_to UUID NULL,
    ADD COLUMN deliverable_document_id UUID NULL,
    ADD COLUMN fulfilled_at TIMESTAMPTZ NULL,
    ADD COLUMN fulfillment_error VARCHAR(500) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_service_requests_assigned ON service_requests (assigned_to)
    WHERE assigned_to IS NOT NULL;

CREATE UNIQUE INDEX uk_service_requests_open_per_type
    ON service_requests (student_id, request_type)
    WHERE status NOT IN ('COMPLETED', 'DENIED');

CREATE TABLE service_request_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id      UUID         NOT NULL REFERENCES service_requests (id) ON DELETE CASCADE,
    from_status     VARCHAR(30)  NULL,
    to_status       VARCHAR(30)  NOT NULL,
    actor_user_id   UUID         NULL,
    note            VARCHAR(2000) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_request_events_request ON service_request_events (request_id, created_at);
