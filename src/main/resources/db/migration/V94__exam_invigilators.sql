-- G6: an exam sitting had a hall and a clock time but no record of who is actually invigilating it.
CREATE TABLE exam_invigilators (
    exam_sitting_id UUID NOT NULL REFERENCES exam_sittings (id),
    user_id         UUID NOT NULL,
    assigned_by     UUID,
    assigned_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (exam_sitting_id, user_id)
);

CREATE INDEX idx_exam_invigilators_sitting ON exam_invigilators (exam_sitting_id);
