-- Programme requirement blocks. Core-vs-elective is a property of the block, never of the course.

CREATE TABLE programme_requirement_blocks (
    id               UUID         PRIMARY KEY,
    programme_id     UUID         NOT NULL,
    name             VARCHAR(200) NOT NULL,
    kind             VARCHAR(30)  NOT NULL,
    required_credits INTEGER      NOT NULL,
    position         INTEGER      NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT uk_requirement_blocks_programme_name UNIQUE (programme_id, name),
    CONSTRAINT fk_requirement_blocks_programme FOREIGN KEY (programme_id) REFERENCES programmes (id),
    CONSTRAINT ck_requirement_blocks_credits CHECK (required_credits > 0),
    CONSTRAINT ck_requirement_blocks_kind CHECK (
        kind IN ('CORE', 'ELECTIVE', 'GENERAL_EDUCATION', 'FREE_ELECTIVE')
    )
);
CREATE INDEX idx_requirement_blocks_programme ON programme_requirement_blocks (programme_id);

CREATE TABLE programme_requirement_courses (
    block_id  UUID NOT NULL,
    course_id UUID NOT NULL,
    CONSTRAINT pk_requirement_courses PRIMARY KEY (block_id, course_id),
    CONSTRAINT fk_requirement_courses_block FOREIGN KEY (block_id)
        REFERENCES programme_requirement_blocks (id) ON DELETE CASCADE,
    CONSTRAINT fk_requirement_courses_course FOREIGN KEY (course_id) REFERENCES courses (id)
);
CREATE INDEX idx_requirement_courses_course ON programme_requirement_courses (course_id);
