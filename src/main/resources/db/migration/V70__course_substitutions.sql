-- Records an approved course substitution: the registrar has decided that passing
-- substitute_course_id satisfies a requirement that names required_course_id. Before this,
-- COURSE_SUBSTITUTION requests validated their payload and recorded nothing — the request would
-- complete, but degree progress and the prerequisite check never learned the substitution had
-- been approved.
CREATE TABLE course_substitutions (
    id                   UUID        PRIMARY KEY,
    student_id           UUID        NOT NULL REFERENCES students (id),
    required_course_id   UUID        NOT NULL REFERENCES courses (id),
    substitute_course_id UUID        NOT NULL REFERENCES courses (id),
    service_request_id   UUID        NOT NULL REFERENCES service_requests (id),
    approved_by          UUID,
    approved_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_course_substitutions_student_required UNIQUE (student_id, required_course_id),
    CONSTRAINT ck_course_substitutions_distinct CHECK (required_course_id <> substitute_course_id)
);
CREATE INDEX idx_course_substitutions_student ON course_substitutions (student_id);
