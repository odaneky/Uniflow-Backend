-- D9: SLA due-date tracking, staff escalation, and inbound student-submitted evidence for
-- service requests.
ALTER TABLE service_requests
    ADD COLUMN due_at            TIMESTAMPTZ,
    ADD COLUMN escalated_at      TIMESTAMPTZ,
    ADD COLUMN escalated_by      UUID,
    ADD COLUMN escalation_reason VARCHAR(1000);

-- Backfill due_at for rows that predate this column, using each type's SLA window
-- (ServiceRequestType.slaDays()) measured from when the request was originally submitted.
UPDATE service_requests SET due_at = created_at + (CASE request_type
    WHEN 'TRANSCRIPT'           THEN INTERVAL '5 days'
    WHEN 'WITHDRAWAL'           THEN INTERVAL '7 days'
    WHEN 'VERIFICATION'         THEN INTERVAL '3 days'
    WHEN 'APPEAL'               THEN INTERVAL '14 days'
    WHEN 'GRADUATION'           THEN INTERVAL '21 days'
    WHEN 'PROFILE_CORRECTION'   THEN INTERVAL '5 days'
    WHEN 'SAP_APPEAL'           THEN INTERVAL '14 days'
    WHEN 'LATE_ADD'             THEN INTERVAL '3 days'
    WHEN 'COURSE_SUBSTITUTION'  THEN INTERVAL '10 days'
    WHEN 'LEAVE_OF_ABSENCE'     THEN INTERVAL '10 days'
    WHEN 'READMISSION'          THEN INTERVAL '14 days'
    WHEN 'PROGRAMME_TRANSFER'   THEN INTERVAL '14 days'
    ELSE INTERVAL '14 days'
END)
WHERE due_at IS NULL;

ALTER TABLE service_requests ALTER COLUMN due_at SET NOT NULL;

-- Partial: only open requests are ever queried for overdue triage.
CREATE INDEX idx_service_requests_due_at ON service_requests (due_at)
    WHERE status NOT IN ('COMPLETED', 'DENIED', 'CANCELLED');

CREATE TABLE service_request_attachments (
    request_id  UUID NOT NULL,
    document_id UUID NOT NULL,
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (request_id, document_id),
    CONSTRAINT fk_service_request_attachments_request
        FOREIGN KEY (request_id) REFERENCES service_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_service_request_attachments_document
        FOREIGN KEY (document_id) REFERENCES documents (id)
);

CREATE INDEX idx_service_request_attachments_request ON service_request_attachments (request_id);
