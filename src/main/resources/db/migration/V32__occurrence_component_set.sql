-- An occurrence (course_sections row, e.g. UN1) is a set. It can hold up to three
-- components — lecture, tutorial, laboratory — each with its own capacity and teacher.
-- Enrolment and the UN code stay on the set.

CREATE TABLE section_components (
    id               UUID         PRIMARY KEY,
    section_id       UUID         NOT NULL,
    component        VARCHAR(20)  NOT NULL,
    capacity         INTEGER      NOT NULL,
    lecturer_user_id UUID,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT uk_section_components_section_kind UNIQUE (section_id, component),
    CONSTRAINT fk_section_components_section FOREIGN KEY (section_id)
        REFERENCES course_sections (id) ON DELETE CASCADE,
    CONSTRAINT fk_section_components_lecturer FOREIGN KEY (lecturer_user_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_section_components_component
        CHECK (component IN ('LECTURE', 'TUTORIAL', 'LABORATORY')),
    CONSTRAINT ck_section_components_capacity CHECK (capacity > 0)
);

CREATE INDEX idx_section_components_section ON section_components (section_id);
CREATE INDEX idx_section_components_lecturer ON section_components (lecturer_user_id);

INSERT INTO section_components (
        id, section_id, component, capacity, lecturer_user_id, created_at, updated_at, created_by, updated_by)
SELECT gen_random_uuid(),
       s.id,
       s.component,
       s.capacity,
       s.lecturer_user_id,
       s.created_at,
       s.updated_at,
       s.created_by,
       s.updated_by
FROM course_sections s;
