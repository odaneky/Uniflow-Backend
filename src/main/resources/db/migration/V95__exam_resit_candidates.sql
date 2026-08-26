-- G6: a resit or deferred paper is an ExamSitting like any other, but it is only relevant to the
-- specific students who failed or were granted a deferral — not the whole section, who would
-- otherwise see a paper on their timetable that has nothing to do with them. A sitting with no
-- candidate rows keeps today's behaviour (visible to the whole section); one with any candidate
-- rows is visible only to those students.
CREATE TABLE exam_resit_candidates (
    exam_sitting_id UUID NOT NULL REFERENCES exam_sittings (id),
    student_id      UUID NOT NULL,
    added_by        UUID,
    added_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (exam_sitting_id, student_id)
);

CREATE INDEX idx_exam_resit_candidates_sitting ON exam_resit_candidates (exam_sitting_id);
