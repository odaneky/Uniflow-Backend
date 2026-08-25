-- C7: a record of every standing decision, separate from term_academic_records (which stores the
-- computed standing itself) and separate from students.status (the enrolment-status field this
-- can drive). Kept even for a term where standing did not change, so "was this student ever
-- reviewed for standing at term close" has an answer independent of whether anything moved.
CREATE TABLE academic_standing_events (
    id                UUID          PRIMARY KEY,
    student_id        UUID          NOT NULL REFERENCES students (id),
    academic_term_id  UUID          NOT NULL REFERENCES academic_terms (id),
    -- The institutional chronological position snapshotted at write, same as grades.term_order:
    -- "most recent standing" must sort by this, not by created_at or effective_from (day
    -- granularity), since two terms can legitimately be closed on the same calendar day.
    term_order        INTEGER       NOT NULL,
    from_standing     VARCHAR(30),
    to_standing       VARCHAR(30)   NOT NULL,
    reason            VARCHAR(500)  NOT NULL,
    -- Cross-module reference into identity; null when the outcome was system-derived rather than a
    -- committee override.
    decided_by        UUID,
    effective_from    DATE          NOT NULL,
    -- Cross-module reference into request; null unless this standing was set through an appeal.
    appeal_request_id UUID,
    created_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_academic_standing_events UNIQUE (student_id, academic_term_id)
);
CREATE INDEX idx_academic_standing_events_student ON academic_standing_events (student_id);
CREATE INDEX idx_academic_standing_events_term ON academic_standing_events (academic_term_id);
