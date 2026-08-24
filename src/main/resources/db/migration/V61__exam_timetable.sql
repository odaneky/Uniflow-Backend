-- Exam timetables: when a sitting happens, where, and which seat.
--
-- WHY A SEPARATE TABLE FROM `assessments`. An assessment is an academic artifact — it carries a
-- weight, a maximum score and a due date, and belongs to the lecturer who set it. A sitting is a
-- logistics record: a hall, a clock time, a seat, usually fixed centrally by an examinations office
-- long after the assessment was written. They change for different reasons and are edited by
-- different people, so they are different rows. Folding a room and a seat onto `assessments` would
-- also imply every quiz needs a hall.
--
-- The two are linked when it matters: `assessment_id` is optional, and set when a sitting is the
-- occasion on which a particular graded assessment is taken.

ALTER TABLE academic_terms
    ADD COLUMN exam_starts_on DATE,
    ADD COLUMN exam_ends_on   DATE;

-- Both ends or neither, and in order. A half-open exam period is ambiguous exactly when a student
-- is asking "are we in exams yet".
ALTER TABLE academic_terms
    ADD CONSTRAINT ck_academic_terms_exam_window CHECK (
        (exam_starts_on IS NULL AND exam_ends_on IS NULL)
        OR (exam_starts_on IS NOT NULL AND exam_ends_on IS NOT NULL AND exam_ends_on >= exam_starts_on)
    );

CREATE TABLE exam_sittings (
    id                UUID          PRIMARY KEY,
    course_section_id UUID          NOT NULL,
    assessment_id     UUID,
    title             VARCHAR(100)  NOT NULL,
    starts_at         TIMESTAMPTZ   NOT NULL,
    duration_minutes  INTEGER       NOT NULL,
    room              VARCHAR(60)   NOT NULL,
    -- Free text on purpose: "Rows 1–12", "Seats 40–78", "Alphabetical A–K". Universities describe
    -- seating in prose, and forcing a numeric range here would be a model nobody could use.
    seating           VARCHAR(120),
    -- A draft timetable is worked on for weeks and is wrong for most of that time. Students see
    -- nothing until the examinations office publishes it.
    published         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT fk_exam_sittings_section FOREIGN KEY (course_section_id)
        REFERENCES course_sections (id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_sittings_assessment FOREIGN KEY (assessment_id)
        REFERENCES assessments (id) ON DELETE SET NULL,
    CONSTRAINT ck_exam_sittings_duration CHECK (duration_minutes BETWEEN 1 AND 600)
);

-- The student query is "my sections' published sittings, soonest first"; the office query is
-- "everything in this room that day". Both are covered.
CREATE INDEX idx_exam_sittings_section ON exam_sittings (course_section_id, starts_at);
CREATE INDEX idx_exam_sittings_when ON exam_sittings (starts_at) WHERE published;
CREATE INDEX idx_exam_sittings_room ON exam_sittings (room, starts_at);
