-- A catalog course includes one or more teaching components. It is not a single delivery type.

CREATE TABLE course_components (
    course_id UUID        NOT NULL,
    component VARCHAR(20) NOT NULL,
    CONSTRAINT pk_course_components PRIMARY KEY (course_id, component),
    CONSTRAINT fk_course_components_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT ck_course_components_value CHECK (component IN ('LECTURE', 'TUTORIAL', 'LABORATORY'))
);
CREATE INDEX idx_course_components_course ON course_components (course_id);

INSERT INTO course_components (course_id, component)
SELECT id,
       CASE course_type
           WHEN 'LABORATORY' THEN 'LABORATORY'
           ELSE 'LECTURE'
       END
FROM courses;

ALTER TABLE courses DROP COLUMN course_type;
