-- Snapshots the course, term and credit value onto the grade at the moment it is awarded.
--
-- Without this, GPA is computed by joining out to courses.credits and academic_terms as they
-- stand *today* — so editing a course's credit value silently rewrites every historic GPA that
-- ever counted it, and sorting a transcript by grades.created_at reverts to an earlier attempt the
-- instant a correction is entered late. Both are fixed by recording, once, what was true at award
-- time: course_id and academic_term_id are for readability and joins, credits and term_order are
-- the values GPA and transcript ordering actually consume. None of the four is ever revised after
-- the row is created — a later correction to the mark does not correct these, because nothing about
-- which course, term or credit value the mark was for actually changed.
--
-- Columns land nullable first and are backfilled before the NOT NULL constraint is added: this
-- migration was originally written ADD COLUMN ... NOT NULL with no backfill, on the assumption that
-- no environment yet had grade rows. That assumption held for a disposable test database but not
-- for a developer's own long-lived one — the moment a single grade exists, the bare NOT NULL add
-- fails outright (a single ALTER TABLE is one DDL statement; Postgres rolls the whole thing back on
-- the first violated constraint, so no later migration could ever repair it). The backfill derives
-- every value from data that already exists — course_section_id resolves to course_id and
-- academic_term_id, courses.credits is copied at snapshot time, and term_order is the same
-- (start_date, sequence_number) ordinal AcademicTermRepository.countUpToAndIncluding computes.
ALTER TABLE grades
    ADD COLUMN course_id        UUID,
    ADD COLUMN academic_term_id UUID,
    ADD COLUMN credits          INTEGER,
    ADD COLUMN term_order       INTEGER;

UPDATE grades g
SET course_id        = cs.course_id,
    academic_term_id = cs.academic_term_id,
    credits           = c.credits
FROM course_sections cs
JOIN courses c ON c.id = cs.course_id
WHERE cs.id = g.course_section_id;

UPDATE grades g
SET term_order = (
    SELECT COUNT(*)
    FROM academic_terms t2
    JOIN academic_terms t1 ON t1.id = g.academic_term_id
    WHERE t2.start_date < t1.start_date
       OR (t2.start_date = t1.start_date AND t2.sequence_number <= t1.sequence_number)
);

ALTER TABLE grades
    ALTER COLUMN course_id        SET NOT NULL,
    ALTER COLUMN academic_term_id SET NOT NULL,
    ALTER COLUMN credits          SET NOT NULL,
    ALTER COLUMN term_order       SET NOT NULL,
    ADD CONSTRAINT fk_grades_course FOREIGN KEY (course_id)        REFERENCES courses (id),
    ADD CONSTRAINT fk_grades_term   FOREIGN KEY (academic_term_id) REFERENCES academic_terms (id);

CREATE INDEX idx_grades_term ON grades (academic_term_id);
