-- Occurrence codes (UN1, S01, …) are unique per course, not university-wide and not per term.
-- COMP2140 and CIT2004 may both have UN1.

ALTER TABLE course_sections DROP CONSTRAINT uk_course_sections_course_term_code;
ALTER TABLE course_sections ADD CONSTRAINT uk_course_sections_course_code UNIQUE (course_id, section_code);
