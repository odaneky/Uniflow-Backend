ALTER TABLE students
    ADD COLUMN advisor_user_id UUID NULL,
    ADD COLUMN advisor_office_hours VARCHAR(200) NULL;

CREATE INDEX idx_students_advisor ON students (advisor_user_id);
