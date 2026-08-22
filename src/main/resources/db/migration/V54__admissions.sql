-- Admissions: applicant applications, supporting documents, and status history.

CREATE TABLE applications (
    id                  UUID           PRIMARY KEY,
    applicant_email     VARCHAR(255)   NOT NULL,
    applicant_name      VARCHAR(200)   NOT NULL,
    programme_id        UUID           NOT NULL,
    academic_term_id    UUID           NOT NULL,
    status              VARCHAR(30)    NOT NULL,
    reference           VARCHAR(20)    NOT NULL,
    payload             JSONB,
    deposit_amount      NUMERIC(12, 2),
    deposit_paid_at     TIMESTAMPTZ,
    student_id          UUID,
    assigned_to         UUID,
    decision_note       VARCHAR(2000),
    decided_by          UUID,
    decided_at          TIMESTAMPTZ,
    submitted_at        TIMESTAMPTZ,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ      NOT NULL,
    updated_at          TIMESTAMPTZ      NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT uk_applications_reference UNIQUE (reference),
    CONSTRAINT fk_applications_programme FOREIGN KEY (programme_id) REFERENCES programmes (id),
    CONSTRAINT fk_applications_term FOREIGN KEY (academic_term_id) REFERENCES academic_terms (id),
    CONSTRAINT fk_applications_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT ck_applications_status CHECK (
        status IN (
            'DRAFT',
            'SUBMITTED',
            'IN_REVIEW',
            'ADMITTED',
            'DENIED',
            'WAITLISTED',
            'MATRICULATED'
        )
    )
);

CREATE INDEX idx_applications_status ON applications (status);
CREATE INDEX idx_applications_programme ON applications (programme_id);
CREATE INDEX idx_applications_term ON applications (academic_term_id);
CREATE INDEX idx_applications_email ON applications (applicant_email);
CREATE INDEX idx_applications_assigned ON applications (assigned_to)
    WHERE assigned_to IS NOT NULL;

CREATE UNIQUE INDEX uk_applications_open_per_term
    ON applications (applicant_email, programme_id, academic_term_id)
    WHERE status NOT IN ('DENIED', 'MATRICULATED');

CREATE TABLE application_documents (
    application_id UUID NOT NULL,
    document_id    UUID NOT NULL,
    PRIMARY KEY (application_id, document_id),
    CONSTRAINT fk_application_documents_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_application_documents_document FOREIGN KEY (document_id) REFERENCES documents (id)
);

CREATE INDEX idx_application_documents_document ON application_documents (document_id);

CREATE TABLE application_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID         NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    from_status     VARCHAR(30)  NULL,
    to_status       VARCHAR(30)  NOT NULL,
    actor_user_id   UUID         NULL,
    note            VARCHAR(2000) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_application_events_application ON application_events (application_id, created_at);
