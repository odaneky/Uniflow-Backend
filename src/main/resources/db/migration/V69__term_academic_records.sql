-- Written once, by term close, and never recomputed — the single source a transcript, standing
-- decision or SAP check can read instead of re-deriving history from every grade every time. A
-- term_academic_records row is the answer to "what was this student's standing at the end of this
-- term", fixed at the moment term close ran, even if a later correction changes a different term's
-- grades.
CREATE TABLE term_academic_records (
    id                        UUID          PRIMARY KEY,
    student_id                UUID          NOT NULL REFERENCES students (id),
    academic_term_id          UUID          NOT NULL REFERENCES academic_terms (id),
    term_order                INTEGER       NOT NULL,
    term_gpa                  NUMERIC(4,2),
    cumulative_gpa            NUMERIC(4,2),
    credits_attempted         INTEGER       NOT NULL,
    credits_earned            INTEGER       NOT NULL,
    cumulative_credits_earned INTEGER       NOT NULL,
    standing                  VARCHAR(30)   NOT NULL,
    computed_at               TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_term_academic_records UNIQUE (student_id, academic_term_id)
);
CREATE INDEX idx_term_academic_records_student ON term_academic_records (student_id);
CREATE INDEX idx_term_academic_records_term ON term_academic_records (academic_term_id);
