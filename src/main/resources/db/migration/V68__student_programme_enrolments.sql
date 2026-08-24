-- Temporal programme membership. students.programme_id remains authoritative for now (see the
-- plan's C5 note: dropping it is deferred to a later pass, since ~50 files across four modules
-- read it via StudentDirectory.StudentSummary) — this table is additive, and
-- StudentProgrammeEnrolmentService is its only writer, kept in step with students.programme_id by
-- writing both in the same transaction. What this adds that the bare field never could: *when* a
-- student was in a programme, and room for more than one open row per student (MINOR,
-- SPECIALISATION) once that is needed.
--
-- curriculum_version_id is nullable, not NOT NULL as first drafted: a programme with no
-- requirement blocks published yet — the overwhelming majority today, since publishing a
-- curriculum version is a brand-new registrar action nobody has taken yet in any existing
-- deployment of this migration — must not block provisioning a student into it.
CREATE TABLE student_programme_enrolments (
    id                    UUID          PRIMARY KEY,
    student_id            UUID          NOT NULL REFERENCES students (id),
    programme_id          UUID          NOT NULL REFERENCES programmes (id),
    curriculum_version_id UUID          REFERENCES curriculum_versions (id),
    kind                  VARCHAR(20)   NOT NULL DEFAULT 'MAJOR',
    is_primary            BOOLEAN       NOT NULL DEFAULT TRUE,
    started_on            DATE          NOT NULL,
    ended_on              DATE,
    end_reason            VARCHAR(30),
    reason                VARCHAR(500),
    approved_by           UUID,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    created_by            VARCHAR(100),
    updated_by            VARCHAR(100),
    CONSTRAINT ck_spe_dates       CHECK (ended_on IS NULL OR ended_on >= started_on),
    CONSTRAINT ck_spe_kind        CHECK (kind IN ('MAJOR', 'MINOR', 'SPECIALISATION')),
    CONSTRAINT ck_spe_end_reason  CHECK (end_reason IS NULL OR end_reason IN ('TRANSFERRED', 'GRADUATED', 'WITHDRAWN'))
);
CREATE INDEX idx_spe_student ON student_programme_enrolments (student_id);
CREATE INDEX idx_spe_programme ON student_programme_enrolments (programme_id);

-- At most one open primary membership per student — the row that answers "what is this student's
-- programme right now," and what StudentProgrammeEnrolmentService keeps aligned with
-- students.programme_id.
CREATE UNIQUE INDEX uk_spe_open_primary ON student_programme_enrolments (student_id)
    WHERE ended_on IS NULL AND is_primary;
