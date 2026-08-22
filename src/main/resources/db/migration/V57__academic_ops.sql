-- Academic operations: attendance sessions, transfer credits, programme types, graduation clearance.

CREATE TABLE attendance_sessions (
    id                UUID        PRIMARY KEY,
    course_section_id UUID        NOT NULL,
    session_date      DATE        NOT NULL,
    topic             VARCHAR(200),
    recorded_by_user_id UUID,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT uk_attendance_sessions_section_date UNIQUE (course_section_id, session_date),
    CONSTRAINT fk_attendance_sessions_section FOREIGN KEY (course_section_id)
        REFERENCES course_sections (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_sessions_recorder FOREIGN KEY (recorded_by_user_id)
        REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_attendance_sessions_section ON attendance_sessions (course_section_id);

CREATE TABLE attendance_marks (
    id          UUID        PRIMARY KEY,
    session_id  UUID        NOT NULL,
    student_id  UUID        NOT NULL,
    status      VARCHAR(30) NOT NULL,
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    CONSTRAINT uk_attendance_marks_session_student UNIQUE (session_id, student_id),
    CONSTRAINT fk_attendance_marks_session FOREIGN KEY (session_id)
        REFERENCES attendance_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_marks_student FOREIGN KEY (student_id) REFERENCES students (id)
);
CREATE INDEX idx_attendance_marks_student ON attendance_marks (student_id);

CREATE TABLE transfer_credits (
    id                  UUID         PRIMARY KEY,
    student_id          UUID         NOT NULL,
    external_institution VARCHAR(200) NOT NULL,
    external_course_code VARCHAR(50)  NOT NULL,
    external_course_title VARCHAR(200),
    internal_course_id  UUID,
    credits_awarded     INTEGER      NOT NULL,
    awarded_at          DATE         NOT NULL,
    note                VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT fk_transfer_credits_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_transfer_credits_course FOREIGN KEY (internal_course_id) REFERENCES courses (id),
    CONSTRAINT ck_transfer_credits_credits CHECK (credits_awarded > 0)
);
CREATE INDEX idx_transfer_credits_student ON transfer_credits (student_id);

ALTER TABLE programmes
    ADD COLUMN programme_type VARCHAR(30) NOT NULL DEFAULT 'DEGREE',
    ADD COLUMN min_graduation_gpa NUMERIC(3, 2);

ALTER TABLE programmes
    ADD CONSTRAINT ck_programmes_type CHECK (programme_type IN ('DEGREE', 'CERTIFICATE'));
ALTER TABLE programmes
    ADD CONSTRAINT ck_programmes_min_graduation_gpa
        CHECK (min_graduation_gpa IS NULL OR (min_graduation_gpa >= 0 AND min_graduation_gpa <= 4));

CREATE TABLE graduation_clearance_items (
    id          UUID         PRIMARY KEY,
    student_id  UUID         NOT NULL,
    item_type   VARCHAR(50)  NOT NULL,
    status      VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    cleared_at  TIMESTAMPTZ,
    cleared_by  UUID,
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    CONSTRAINT uk_graduation_clearance_student_type UNIQUE (student_id, item_type),
    CONSTRAINT fk_graduation_clearance_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_graduation_clearance_cleared_by FOREIGN KEY (cleared_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_graduation_clearance_status CHECK (status IN ('PENDING', 'CLEARED', 'WAIVED'))
);
CREATE INDEX idx_graduation_clearance_student ON graduation_clearance_items (student_id);
