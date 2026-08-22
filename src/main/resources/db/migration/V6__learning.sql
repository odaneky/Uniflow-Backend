-- Teaching material: CourseContent -> LearningModule -> Lesson -> LearningMaterial.

CREATE TABLE course_contents (
    id                UUID        PRIMARY KEY,
    course_section_id UUID        NOT NULL,
    overview          VARCHAR(4000),
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT uk_course_contents_section UNIQUE (course_section_id),
    CONSTRAINT fk_course_contents_section FOREIGN KEY (course_section_id) REFERENCES course_sections (id) ON DELETE CASCADE
);

CREATE TABLE learning_modules (
    id                UUID         PRIMARY KEY,
    course_content_id UUID         NOT NULL,
    title             VARCHAR(200) NOT NULL,
    position          INTEGER      NOT NULL,
    published         BOOLEAN      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT fk_learning_modules_content FOREIGN KEY (course_content_id) REFERENCES course_contents (id) ON DELETE CASCADE
);
CREATE INDEX idx_learning_modules_content ON learning_modules (course_content_id);

CREATE TABLE lessons (
    id                 UUID         PRIMARY KEY,
    learning_module_id UUID         NOT NULL,
    title              VARCHAR(200) NOT NULL,
    summary            VARCHAR(2000),
    position           INTEGER      NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT fk_lessons_module FOREIGN KEY (learning_module_id) REFERENCES learning_modules (id) ON DELETE CASCADE
);
CREATE INDEX idx_lessons_module ON lessons (learning_module_id);

CREATE TABLE learning_materials (
    id            UUID         PRIMARY KEY,
    lesson_id     UUID         NOT NULL,
    title         VARCHAR(200) NOT NULL,
    material_type VARCHAR(30)  NOT NULL,
    external_url  VARCHAR(1000),
    document_id   UUID,
    position      INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    CONSTRAINT fk_learning_materials_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE
);
CREATE INDEX idx_learning_materials_lesson ON learning_materials (lesson_id);
