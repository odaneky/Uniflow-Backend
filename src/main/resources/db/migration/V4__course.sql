-- Course catalog and the term-specific offerings students actually join.

CREATE TABLE courses (
    id            UUID         PRIMARY KEY,
    course_code   VARCHAR(20)  NOT NULL,
    title         VARCHAR(200) NOT NULL,
    description   VARCHAR(4000),
    credits       INTEGER      NOT NULL,
    level         INTEGER      NOT NULL,
    department_id UUID         NOT NULL,
    course_type   VARCHAR(30)  NOT NULL,
    status        VARCHAR(30)  NOT NULL,
    version       BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    CONSTRAINT uk_courses_course_code UNIQUE (course_code),
    CONSTRAINT fk_courses_department  FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT ck_courses_credits     CHECK (credits > 0),
    CONSTRAINT ck_courses_level       CHECK (level BETWEEN 1 AND 9)
);
CREATE INDEX idx_courses_department ON courses (department_id);
CREATE INDEX idx_courses_status     ON courses (status);

CREATE TABLE course_sections (
    id               UUID        PRIMARY KEY,
    course_id        UUID        NOT NULL,
    academic_term_id UUID        NOT NULL,
    section_code     VARCHAR(20) NOT NULL,
    lecturer_user_id UUID,
    capacity         INTEGER     NOT NULL,
    enrolled_count   INTEGER     NOT NULL,
    status           VARCHAR(30) NOT NULL,
    version          BIGINT      NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT uk_course_sections_course_term_code UNIQUE (course_id, academic_term_id, section_code),
    CONSTRAINT fk_course_sections_course   FOREIGN KEY (course_id)        REFERENCES courses (id),
    CONSTRAINT fk_course_sections_term     FOREIGN KEY (academic_term_id) REFERENCES academic_terms (id),
    CONSTRAINT fk_course_sections_lecturer FOREIGN KEY (lecturer_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_course_sections_capacity CHECK (capacity > 0),
    -- The backstop for the seat counter. The guarded UPDATE in CourseSectionRepository is what
    -- keeps the count correct under concurrency; this constraint guarantees that any future code
    -- path which tries to write the counter directly fails loudly instead of over-filling a room.
    CONSTRAINT ck_course_sections_enrolled CHECK (enrolled_count >= 0 AND enrolled_count <= capacity)
);
CREATE INDEX idx_course_sections_course   ON course_sections (course_id);
CREATE INDEX idx_course_sections_term     ON course_sections (academic_term_id);
CREATE INDEX idx_course_sections_lecturer ON course_sections (lecturer_user_id);
