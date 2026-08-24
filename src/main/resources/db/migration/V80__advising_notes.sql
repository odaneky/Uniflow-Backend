-- G8 (partial): advising had no depth beyond assignment and office hours — no way for an advisor
-- to record what was discussed or agreed with an advisee, and nothing for the next advisor to read
-- if the assignment changes. Notes are visible only to the student's current advisor and registry
-- staff, not broadcast to every role that happens to be staff.
CREATE TABLE advising_notes (
    id               UUID          PRIMARY KEY,
    student_id       UUID          NOT NULL REFERENCES students (id),
    advisor_user_id  UUID          NOT NULL,
    note             VARCHAR(2000) NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100)
);
CREATE INDEX idx_advising_notes_student ON advising_notes (student_id, created_at DESC);
