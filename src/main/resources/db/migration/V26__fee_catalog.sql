-- Extra fees the university can add or adjust (lab, equipment, miscellaneous, …).
-- Charged at enrolment according to assessment; existing ledger rows are not rewritten.

CREATE TABLE fee_catalog (
    id             UUID           PRIMARY KEY,
    name           VARCHAR(80)    NOT NULL,
    description    VARCHAR(500)   NOT NULL,
    amount         NUMERIC(12,2)  NOT NULL,
    kind           VARCHAR(30)    NOT NULL,
    assessment     VARCHAR(30)    NOT NULL,
    course_id      UUID,
    programme_id   UUID,
    active         BOOLEAN        NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    CONSTRAINT ck_fee_catalog_amount CHECK (amount > 0),
    CONSTRAINT ck_fee_catalog_kind
        CHECK (kind IN ('MANDATORY', 'LAB', 'EQUIPMENT', 'MISCELLANEOUS')),
    CONSTRAINT ck_fee_catalog_assessment
        CHECK (assessment IN ('ONCE_PER_TERM', 'PER_ENROLMENT', 'PER_CREDIT')),
    CONSTRAINT ck_fee_catalog_course_assessment
        CHECK (course_id IS NULL OR assessment <> 'ONCE_PER_TERM'),
    CONSTRAINT fk_fee_catalog_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_fee_catalog_programme FOREIGN KEY (programme_id) REFERENCES programmes (id)
);
CREATE INDEX idx_fee_catalog_active ON fee_catalog (active);

INSERT INTO fee_catalog (
    id, name, description, amount, kind, assessment, course_id, programme_id, active,
    created_at, updated_at
) VALUES
    (
        'aaaaaaaa-aaaa-4aaa-8aaa-000000000011',
        'Registration Fee',
        'Charged once each term at first enrolment.',
        500.00,
        'MANDATORY',
        'ONCE_PER_TERM',
        NULL,
        NULL,
        TRUE,
        NOW(),
        NOW()
    ),
    (
        'aaaaaaaa-aaaa-4aaa-8aaa-000000000012',
        'Technology Fee',
        'Campus systems, network, and LMS access.',
        100.00,
        'MANDATORY',
        'ONCE_PER_TERM',
        NULL,
        NULL,
        TRUE,
        NOW(),
        NOW()
    );
