-- Attendance registers.

CREATE TABLE attendance_records (
    id                  UUID        PRIMARY KEY,
    course_section_id   UUID        NOT NULL,
    student_id          UUID        NOT NULL,
    session_date        DATE        NOT NULL,
    status              VARCHAR(30) NOT NULL,
    recorded_by_user_id UUID,
    note                VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    -- Re-submitting a register must correct the existing row, not append a second one, or every
    -- attendance percentage derived from this table silently drifts.
    CONSTRAINT uk_attendance_section_student_date UNIQUE (course_section_id, student_id, session_date),
    CONSTRAINT fk_attendance_section  FOREIGN KEY (course_section_id)   REFERENCES course_sections (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_student  FOREIGN KEY (student_id)          REFERENCES students (id),
    CONSTRAINT fk_attendance_recorder FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_attendance_student      ON attendance_records (student_id);
CREATE INDEX idx_attendance_section_date ON attendance_records (course_section_id, session_date);
