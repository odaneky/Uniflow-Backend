-- E5: students.residency_classification was added in V53 with a comment saying it drives the
-- tuition tier, but no Java code ever read it — every student was charged the same rate
-- regardless of residency. This adds per-tier institution-wide rates, consulted by
-- TuitionScheduleService.quoteFor when the student has no programme-specific override.

ALTER TABLE students
    ADD CONSTRAINT ck_students_residency_classification
        CHECK (residency_classification IN ('IN_DISTRICT', 'OUT_OF_DISTRICT', 'OUT_OF_STATE'));

CREATE TABLE residency_tuition_rates (
    id                        UUID           PRIMARY KEY,
    residency_classification  VARCHAR(30)    NOT NULL,
    amount_per_credit         NUMERIC(12,2)  NOT NULL,
    created_at                TIMESTAMPTZ    NOT NULL,
    updated_at                TIMESTAMPTZ    NOT NULL,
    created_by                VARCHAR(100),
    updated_by                VARCHAR(100),
    CONSTRAINT uk_residency_tuition_rates_classification UNIQUE (residency_classification),
    CONSTRAINT ck_residency_tuition_rates_amount CHECK (amount_per_credit > 0),
    CONSTRAINT ck_residency_tuition_rates_classification
        CHECK (residency_classification IN ('IN_DISTRICT', 'OUT_OF_DISTRICT', 'OUT_OF_STATE'))
);
