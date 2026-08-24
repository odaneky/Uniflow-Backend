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
ALTER TABLE grades
    ADD COLUMN course_id        UUID    NOT NULL,
    ADD COLUMN academic_term_id UUID    NOT NULL,
    ADD COLUMN credits          INTEGER NOT NULL,
    ADD COLUMN term_order       INTEGER NOT NULL,
    ADD CONSTRAINT fk_grades_course FOREIGN KEY (course_id)        REFERENCES courses (id),
    ADD CONSTRAINT fk_grades_term   FOREIGN KEY (academic_term_id) REFERENCES academic_terms (id);

CREATE INDEX idx_grades_term ON grades (academic_term_id);
