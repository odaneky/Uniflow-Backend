-- Assessed work and student attempts at it.

CREATE TABLE assessments (
    id                UUID          PRIMARY KEY,
    course_section_id UUID          NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    instructions      VARCHAR(4000),
    assessment_type   VARCHAR(30)   NOT NULL,
    max_score         NUMERIC(7,2)  NOT NULL,
    weight_percent    NUMERIC(5,2)  NOT NULL,
    due_at            TIMESTAMPTZ,
    published         BOOLEAN       NOT NULL,
    version           BIGINT        NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT fk_assessments_section FOREIGN KEY (course_section_id) REFERENCES course_sections (id) ON DELETE CASCADE,
    CONSTRAINT ck_assessments_max_score CHECK (max_score > 0),
    CONSTRAINT ck_assessments_weight    CHECK (weight_percent >= 0 AND weight_percent <= 100)
);
CREATE INDEX idx_assessments_section ON assessments (course_section_id);
CREATE INDEX idx_assessments_due     ON assessments (due_at);

CREATE TABLE assessment_attempts (
    id             UUID         PRIMARY KEY,
    assessment_id  UUID         NOT NULL,
    student_id     UUID         NOT NULL,
    attempt_number INTEGER      NOT NULL,
    status         VARCHAR(30)  NOT NULL,
    submitted_at   TIMESTAMPTZ,
    raw_score      NUMERIC(7,2),
    version        BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    -- Makes a resubmission a new numbered attempt rather than an overwrite of the previous one.
    CONSTRAINT uk_assessment_attempts_assessment_student_number
        UNIQUE (assessment_id, student_id, attempt_number),
    CONSTRAINT fk_assessment_attempts_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE CASCADE,
    CONSTRAINT fk_assessment_attempts_student    FOREIGN KEY (student_id)    REFERENCES students (id),
    CONSTRAINT ck_assessment_attempts_number     CHECK (attempt_number > 0),
    CONSTRAINT ck_assessment_attempts_score      CHECK (raw_score IS NULL OR raw_score >= 0)
);
CREATE INDEX idx_assessment_attempts_assessment ON assessment_attempts (assessment_id);
CREATE INDEX idx_assessment_attempts_student    ON assessment_attempts (student_id);
