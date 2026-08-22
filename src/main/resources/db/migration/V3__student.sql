-- Student records. The profile table is created first because students owns the FK to it.

CREATE TABLE student_profiles (
    id                      UUID        PRIMARY KEY,
    phone_number            VARCHAR(30),
    date_of_birth           DATE,
    nationality             VARCHAR(100),
    address_line1           VARCHAR(255),
    address_line2           VARCHAR(255),
    city                    VARCHAR(100),
    country                 VARCHAR(100),
    emergency_contact_name  VARCHAR(200),
    emergency_contact_phone VARCHAR(30),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100)
);

CREATE TABLE students (
    id                       UUID        PRIMARY KEY,
    user_id                  UUID        NOT NULL,
    student_number           VARCHAR(30) NOT NULL,
    programme_id             UUID        NOT NULL,
    status                   VARCHAR(30) NOT NULL,
    admission_date           DATE        NOT NULL,
    expected_graduation_date DATE,
    profile_id               UUID        NOT NULL,
    version                  BIGINT      NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    created_by               VARCHAR(100),
    updated_by               VARCHAR(100),
    -- One student record per login account, and one matriculation number per student. These two
    -- indexes are the actual defence against a duplicate created by two concurrent requests.
    CONSTRAINT uk_students_student_number UNIQUE (student_number),
    CONSTRAINT uk_students_user           UNIQUE (user_id),
    CONSTRAINT uk_students_profile        UNIQUE (profile_id),
    CONSTRAINT fk_students_user      FOREIGN KEY (user_id)      REFERENCES users (id),
    CONSTRAINT fk_students_programme FOREIGN KEY (programme_id) REFERENCES programmes (id),
    CONSTRAINT fk_students_profile   FOREIGN KEY (profile_id)   REFERENCES student_profiles (id)
);
CREATE INDEX idx_students_programme ON students (programme_id);
CREATE INDEX idx_students_status    ON students (status);
