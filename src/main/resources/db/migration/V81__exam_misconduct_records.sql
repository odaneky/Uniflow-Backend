CREATE TABLE exam_misconduct_records (
    id                UUID          PRIMARY KEY,
    exam_sitting_id   UUID          NOT NULL REFERENCES exam_sittings (id) ON DELETE CASCADE,
    student_id        UUID          NOT NULL REFERENCES students (id),
    description       VARCHAR(2000) NOT NULL,
    reported_by       UUID          NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100)
);

CREATE INDEX idx_exam_misconduct_sitting ON exam_misconduct_records (exam_sitting_id, created_at DESC);
CREATE INDEX idx_exam_misconduct_student ON exam_misconduct_records (student_id, created_at DESC);
