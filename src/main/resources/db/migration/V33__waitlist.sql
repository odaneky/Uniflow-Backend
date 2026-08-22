-- A waitlisted row occupies the (student, section) slot the same way an active seat does, so the
-- student cannot also enrol or join the list twice. It does not occupy capacity.

DROP INDEX IF EXISTS uk_enrollments_student_section_active;

CREATE UNIQUE INDEX uk_enrollments_student_section_active
    ON enrollments (student_id, course_section_id)
    WHERE status IN ('ENROLLED', 'PENDING', 'WAITLISTED');
