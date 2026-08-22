-- Each offering is one teaching component. A 120-seat lecture and a 40-seat tutorial
-- are different course_sections, each with its own capacity and lecturer. Meetings on
-- that row are the timetable for that component only.

ALTER TABLE course_sections
    ADD COLUMN component VARCHAR(20) NOT NULL DEFAULT 'LECTURE';

ALTER TABLE course_sections
    ADD CONSTRAINT ck_course_sections_component
        CHECK (component IN ('LECTURE', 'TUTORIAL', 'LABORATORY'));

UPDATE course_sections s
SET component = 'TUTORIAL'
WHERE EXISTS (
        SELECT 1 FROM section_meetings m
        WHERE m.section_id = s.id AND m.session_type = 'Tutorial')
  AND NOT EXISTS (
        SELECT 1 FROM section_meetings m
        WHERE m.section_id = s.id AND m.session_type IN ('Lecture', 'Lab'));

UPDATE course_sections s
SET component = 'LABORATORY'
WHERE EXISTS (
        SELECT 1 FROM section_meetings m
        WHERE m.section_id = s.id AND m.session_type = 'Lab')
  AND NOT EXISTS (
        SELECT 1 FROM section_meetings m
        WHERE m.section_id = s.id AND m.session_type IN ('Lecture', 'Tutorial'));
