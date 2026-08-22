-- Marking schemes and awarded grades. Deliberately independent of the assessment tables:
-- grades.assessment_id carries no foreign key, so a grade can exist for an overall section
-- result, and grading remains usable if assessment changes shape.

CREATE TABLE grade_scales (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    CONSTRAINT uk_grade_scales_name UNIQUE (name)
);

CREATE TABLE grade_scale_bands (
    id             UUID         PRIMARY KEY,
    grade_scale_id UUID         NOT NULL,
    letter         VARCHAR(5)   NOT NULL,
    min_percent    NUMERIC(5,2) NOT NULL,
    max_percent    NUMERIC(5,2) NOT NULL,
    grade_point    NUMERIC(4,2) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    CONSTRAINT fk_grade_scale_bands_scale FOREIGN KEY (grade_scale_id) REFERENCES grade_scales (id) ON DELETE CASCADE,
    CONSTRAINT ck_grade_scale_bands_range CHECK (
        min_percent >= 0 AND max_percent <= 100 AND max_percent >= min_percent
    )
);
CREATE INDEX idx_grade_scale_bands_scale ON grade_scale_bands (grade_scale_id);

CREATE TABLE grades (
    id                UUID         PRIMARY KEY,
    student_id        UUID         NOT NULL,
    course_section_id UUID         NOT NULL,
    assessment_id     UUID,
    grade_scale_id    UUID         NOT NULL,
    percentage        NUMERIC(5,2) NOT NULL,
    letter            VARCHAR(5)   NOT NULL,
    grade_point       NUMERIC(4,2) NOT NULL,
    published         BOOLEAN      NOT NULL,
    version           BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT fk_grades_student FOREIGN KEY (student_id)        REFERENCES students (id),
    CONSTRAINT fk_grades_section FOREIGN KEY (course_section_id) REFERENCES course_sections (id),
    CONSTRAINT fk_grades_scale   FOREIGN KEY (grade_scale_id)    REFERENCES grade_scales (id),
    CONSTRAINT ck_grades_percentage CHECK (percentage >= 0 AND percentage <= 100)
);
CREATE INDEX idx_grades_student    ON grades (student_id);
CREATE INDEX idx_grades_section    ON grades (course_section_id);
CREATE INDEX idx_grades_assessment ON grades (assessment_id);
-- At most one overall (non-assessment) grade per student per section.
CREATE UNIQUE INDEX uk_grades_student_section_overall
    ON grades (student_id, course_section_id) WHERE assessment_id IS NULL;
