-- Institution-wide semester load and tuition, with optional per-programme overrides.
-- Changing a rate does not rewrite ledger rows already posted.

ALTER TABLE programmes
    ADD COLUMN min_semester_credits INTEGER,
    ADD COLUMN max_semester_credits INTEGER;
ALTER TABLE programmes
    ADD CONSTRAINT ck_programmes_min_semester_credits
        CHECK (min_semester_credits IS NULL OR min_semester_credits BETWEEN 1 AND 40);
ALTER TABLE programmes
    ADD CONSTRAINT ck_programmes_max_semester_credits
        CHECK (max_semester_credits IS NULL OR max_semester_credits BETWEEN 1 AND 40);

CREATE TABLE institution_academic_policies (
    id                     UUID         PRIMARY KEY,
    min_semester_credits   INTEGER      NOT NULL,
    max_semester_credits   INTEGER      NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    created_by             VARCHAR(100),
    updated_by             VARCHAR(100),
    CONSTRAINT ck_institution_academic_policy_range
        CHECK (min_semester_credits BETWEEN 1 AND 40
           AND max_semester_credits BETWEEN 1 AND 40
           AND min_semester_credits <= max_semester_credits)
);

INSERT INTO institution_academic_policies (
    id, min_semester_credits, max_semester_credits, created_at, updated_at
) VALUES (
    'aaaaaaaa-aaaa-4aaa-8aaa-000000000001', 12, 18, NOW(), NOW()
);

CREATE TABLE tuition_schedules (
    id                 UUID           PRIMARY KEY,
    amount_per_credit  NUMERIC(12,2)  NOT NULL,
    campus_fee         NUMERIC(12,2)  NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT ck_tuition_schedules_amounts
        CHECK (amount_per_credit > 0 AND campus_fee > 0)
);

INSERT INTO tuition_schedules (
    id, amount_per_credit, campus_fee, created_at, updated_at
) VALUES (
    'aaaaaaaa-aaaa-4aaa-8aaa-000000000002', 200.00, 350.00, NOW(), NOW()
);

CREATE TABLE programme_tuition_rates (
    id                 UUID           PRIMARY KEY,
    programme_id       UUID           NOT NULL,
    amount_per_credit  NUMERIC(12,2)  NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT uk_programme_tuition_rates_programme UNIQUE (programme_id),
    CONSTRAINT fk_programme_tuition_rates_programme
        FOREIGN KEY (programme_id) REFERENCES programmes (id),
    CONSTRAINT ck_programme_tuition_rates_amount CHECK (amount_per_credit > 0)
);
