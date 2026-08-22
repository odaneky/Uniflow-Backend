-- Course enrolment rules. Core-vs-elective stays on the programme; these clauses are a
-- property of the course. Groups on one course are ANDed. Options inside a group are ORed.
-- Co-requisites may be in progress in the same term; prerequisites must already be completed.

CREATE TABLE course_requirement_groups (
    id             UUID         PRIMARY KEY,
    course_id      UUID         NOT NULL,
    position       INTEGER      NOT NULL,
    kind           VARCHAR(20)  NOT NULL,
    minimum_level  INTEGER,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    CONSTRAINT uk_course_requirement_groups UNIQUE (course_id, position),
    CONSTRAINT fk_course_requirement_groups_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT ck_course_requirement_kind CHECK (kind IN ('PREREQUISITE', 'COREQUISITE', 'MINIMUM_LEVEL')),
    CONSTRAINT ck_course_requirement_level CHECK (
        minimum_level IS NULL OR minimum_level BETWEEN 1 AND 9
    )
);
CREATE INDEX idx_course_requirement_groups_course ON course_requirement_groups (course_id);

CREATE TABLE course_requirement_options (
    group_id           UUID NOT NULL,
    required_course_id UUID NOT NULL,
    CONSTRAINT pk_course_requirement_options PRIMARY KEY (group_id, required_course_id),
    CONSTRAINT fk_course_requirement_options_group FOREIGN KEY (group_id)
        REFERENCES course_requirement_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_course_requirement_options_course FOREIGN KEY (required_course_id) REFERENCES courses (id)
);
CREATE INDEX idx_course_requirement_options_course ON course_requirement_options (required_course_id);
