-- E9: scholarship programmes — named funds a student is awarded from, distinct from the
-- undifferentiated PELL/INSTITUTIONAL/LOAN award types financial_aid_awards already had.
CREATE TABLE scholarship_programmes (
    id                   UUID PRIMARY KEY,
    name                 VARCHAR(200) NOT NULL,
    -- Null: institution-funded. Set: a named donor, foundation or company funds this programme.
    sponsor_name         VARCHAR(200),
    description          VARCHAR(2000),
    default_amount       NUMERIC(12,2) NOT NULL,
    renewable            BOOLEAN NOT NULL DEFAULT FALSE,
    max_renewals         INTEGER,
    eligibility_criteria VARCHAR(2000),
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    created_by           VARCHAR(100),
    updated_by           VARCHAR(100),
    CONSTRAINT uk_scholarship_programmes_name UNIQUE (name),
    CONSTRAINT ck_scholarship_programmes_max_renewals CHECK (max_renewals IS NULL OR max_renewals >= 0)
);

ALTER TABLE financial_aid_awards
    ADD COLUMN scholarship_programme_id UUID REFERENCES scholarship_programmes (id),
    ADD COLUMN renewed_from_award_id UUID REFERENCES financial_aid_awards (id);

CREATE INDEX idx_financial_aid_awards_scholarship ON financial_aid_awards (scholarship_programme_id);

-- A student may legitimately hold more than one scholarship at once (a merit award and a
-- departmental one, say) — unlike PELL/INSTITUTIONAL/LOAN, which really are one-per-term. The
-- blanket (student, term, award_type) constraint below would otherwise cap every student at
-- exactly one scholarship ever, system-wide, which contradicts "programmes" being plural.
DROP INDEX uk_financial_aid_awards_student_term_type;
CREATE UNIQUE INDEX uk_financial_aid_awards_student_term_type
    ON financial_aid_awards (student_id, academic_term_id, award_type)
    WHERE award_type <> 'SCHOLARSHIP';
CREATE UNIQUE INDEX uk_financial_aid_awards_student_term_scholarship
    ON financial_aid_awards (student_id, academic_term_id, scholarship_programme_id)
    WHERE award_type = 'SCHOLARSHIP';
