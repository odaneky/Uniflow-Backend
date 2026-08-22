-- Financial aid intake, awards, SAP evaluations, and registration service holds.

CREATE TABLE isir_snapshots (
    id              UUID          PRIMARY KEY,
    student_id      UUID          NOT NULL,
    aid_year        VARCHAR(9)    NOT NULL,
    efc             NUMERIC(12, 2),
    pell_eligible   BOOLEAN       NOT NULL DEFAULT FALSE,
    raw_json        TEXT,
    imported_at     TIMESTAMPTZ   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT fk_isir_snapshots_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT uk_isir_snapshots_student_year UNIQUE (student_id, aid_year)
);
CREATE INDEX idx_isir_snapshots_student ON isir_snapshots (student_id, aid_year DESC);

CREATE TABLE financial_aid_awards (
    id                UUID          PRIMARY KEY,
    student_id        UUID          NOT NULL,
    academic_term_id  UUID          NOT NULL,
    award_type        VARCHAR(20)   NOT NULL,
    amount            NUMERIC(12, 2) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    disbursed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT fk_financial_aid_awards_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_financial_aid_awards_term FOREIGN KEY (academic_term_id) REFERENCES academic_terms (id),
    CONSTRAINT ck_financial_aid_awards_amount CHECK (amount > 0)
);
CREATE INDEX idx_financial_aid_awards_student ON financial_aid_awards (student_id, academic_term_id);

CREATE TABLE sap_evaluations (
    id                UUID          PRIMARY KEY,
    student_id        UUID          NOT NULL,
    academic_term_id  UUID          NOT NULL,
    gpa               NUMERIC(4, 2),
    completion_rate   NUMERIC(5, 4),
    meets_sap         BOOLEAN       NOT NULL,
    evaluated_at      TIMESTAMPTZ   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT fk_sap_evaluations_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_sap_evaluations_term FOREIGN KEY (academic_term_id) REFERENCES academic_terms (id)
);
CREATE INDEX idx_sap_evaluations_student ON sap_evaluations (student_id, academic_term_id, evaluated_at DESC);

CREATE TABLE service_holds (
    id          UUID          PRIMARY KEY,
    student_id  UUID          NOT NULL,
    hold_type   VARCHAR(20)   NOT NULL,
    reason      VARCHAR(500)  NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    placed_at   TIMESTAMPTZ   NOT NULL,
    cleared_at  TIMESTAMPTZ,
    placed_by   UUID,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    CONSTRAINT fk_service_holds_student FOREIGN KEY (student_id) REFERENCES students (id)
);
CREATE INDEX idx_service_holds_student_active ON service_holds (student_id, active, placed_at DESC);
