-- A drop (or withdrawal) must not permanently occupy (student, section). Students re-select
-- during registration by creating a new enrolment row; only an active seat is unique.

ALTER TABLE enrollments DROP CONSTRAINT uk_enrollments_student_section;

CREATE UNIQUE INDEX uk_enrollments_student_section_active
    ON enrollments (student_id, course_section_id)
    WHERE status IN ('ENROLLED', 'PENDING');
