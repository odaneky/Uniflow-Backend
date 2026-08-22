-- University-configurable tuition installment schedule, scoped to a term.
-- Each row is a cumulative percent that must be paid by a date (often "before week N").
-- Failure can place a financial hold and/or block exams — both flags are set by administration.

CREATE TABLE payment_plans (
    id                 UUID         PRIMARY KEY,
    academic_term_id   UUID         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT uk_payment_plans_term UNIQUE (academic_term_id)
);

CREATE TABLE payment_installments (
    id                   UUID         PRIMARY KEY,
    plan_id              UUID         NOT NULL,
    position             INTEGER      NOT NULL,
    label                VARCHAR(80)  NOT NULL,
    cumulative_percent   SMALLINT     NOT NULL,
    week_of_term         SMALLINT,
    due_on               DATE         NOT NULL,
    places_hold          BOOLEAN      NOT NULL DEFAULT TRUE,
    blocks_exams         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    created_by           VARCHAR(100),
    updated_by           VARCHAR(100),
    CONSTRAINT uk_payment_installments_position UNIQUE (plan_id, position),
    CONSTRAINT fk_payment_installments_plan FOREIGN KEY (plan_id) REFERENCES payment_plans (id) ON DELETE CASCADE,
    CONSTRAINT ck_payment_installments_percent CHECK (cumulative_percent BETWEEN 1 AND 100),
    CONSTRAINT ck_payment_installments_week CHECK (week_of_term IS NULL OR week_of_term BETWEEN 1 AND 20)
);
CREATE INDEX idx_payment_installments_plan ON payment_installments (plan_id);
