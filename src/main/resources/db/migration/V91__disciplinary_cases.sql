-- G7: disciplinary cases, kept out of the binary staff/student model everything else in this
-- codebase uses. Confidentiality here is per-case, not per-role: filing is any staff member (a
-- lecturer reporting what they witnessed), but only the registry and whichever staff member is
-- assigned to a given case may read it once filed — enforced in DisciplinaryCaseService, not by a
-- role a URL matcher alone could express, since the assigned officer varies case by case.
CREATE TABLE disciplinary_cases (
    id                        UUID          PRIMARY KEY,
    case_number               VARCHAR(20)   NOT NULL,
    student_id                UUID          NOT NULL,
    category                  VARCHAR(30)   NOT NULL,
    status                    VARCHAR(20)   NOT NULL,
    summary                   VARCHAR(2000) NOT NULL,
    filed_by_user_id          UUID          NOT NULL,
    assigned_officer_user_id  UUID,
    outcome                   VARCHAR(30),
    outcome_reason            VARCHAR(2000),
    filed_at                  TIMESTAMPTZ   NOT NULL,
    resolved_at               TIMESTAMPTZ,
    created_at                TIMESTAMPTZ   NOT NULL,
    updated_at                TIMESTAMPTZ   NOT NULL,
    created_by                VARCHAR(100),
    updated_by                VARCHAR(100),
    CONSTRAINT fk_disciplinary_cases_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_disciplinary_cases_filed_by FOREIGN KEY (filed_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_disciplinary_cases_officer FOREIGN KEY (assigned_officer_user_id) REFERENCES users (id),
    CONSTRAINT uk_disciplinary_cases_number UNIQUE (case_number),
    CONSTRAINT ck_disciplinary_cases_category
        CHECK (category IN ('ACADEMIC_INTEGRITY', 'CONDUCT', 'HARASSMENT', 'OTHER')),
    CONSTRAINT ck_disciplinary_cases_status
        CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_disciplinary_cases_outcome
        CHECK (outcome IS NULL OR outcome IN ('WARNING', 'PROBATION', 'SUSPENSION', 'EXPULSION', 'NO_ACTION')),
    -- A case only carries an outcome once it has actually been decided.
    CONSTRAINT ck_disciplinary_cases_resolution
        CHECK ((status IN ('RESOLVED', 'DISMISSED')) = (resolved_at IS NOT NULL AND outcome IS NOT NULL))
);
CREATE INDEX idx_disciplinary_cases_student ON disciplinary_cases (student_id, filed_at);
CREATE INDEX idx_disciplinary_cases_officer ON disciplinary_cases (assigned_officer_user_id);

-- Case history: who reported what, and when — append-only, the same shape advising_notes uses.
CREATE TABLE disciplinary_case_notes (
    id              UUID          PRIMARY KEY,
    case_id         UUID          NOT NULL,
    author_user_id  UUID          NOT NULL,
    note            VARCHAR(2000) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT fk_disciplinary_case_notes_case FOREIGN KEY (case_id) REFERENCES disciplinary_cases (id),
    CONSTRAINT fk_disciplinary_case_notes_author FOREIGN KEY (author_user_id) REFERENCES users (id)
);
CREATE INDEX idx_disciplinary_case_notes_case ON disciplinary_case_notes (case_id, created_at);
