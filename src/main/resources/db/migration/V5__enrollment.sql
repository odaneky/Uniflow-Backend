-- Registration of students into course sections.

CREATE TABLE enrollments (
    id                UUID        PRIMARY KEY,
    student_id        UUID        NOT NULL,
    course_section_id UUID        NOT NULL,
    status            VARCHAR(30) NOT NULL,
    enrolled_at       TIMESTAMPTZ NOT NULL,
    ended_at          TIMESTAMPTZ,
    version           BIGINT      NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    -- THE constraint that makes concurrent duplicate enrolment impossible. The application check
    -- in EnrollmentService is a courtesy for the common case; this is the guarantee.
    CONSTRAINT uk_enrollments_student_section UNIQUE (student_id, course_section_id),
    CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id)        REFERENCES students (id),
    CONSTRAINT fk_enrollments_section FOREIGN KEY (course_section_id) REFERENCES course_sections (id)
);
CREATE INDEX idx_enrollments_student ON enrollments (student_id);
CREATE INDEX idx_enrollments_section ON enrollments (course_section_id);
CREATE INDEX idx_enrollments_status  ON enrollments (status);
