-- C8: tuition_schedules and programme_tuition_rates were overwritten in place — replacing a rate
-- destroyed the only record of what it used to be, so "what was the per-credit rate in Fall 2025"
-- had no answer once a later change landed. Both become effective-dated interval sequences: at
-- most one row per key (institution-wide for tuition_schedules, per-programme for
-- programme_tuition_rates) is ever open (effective_to IS NULL) at a time, the same "at most one
-- open row" shape uk_curriculum_versions_one_published already established for curriculum
-- versions. Resolution is "the row whose interval contains the charge date" — for now, always
-- today's date, since nothing yet needs to price a past or future date.

ALTER TABLE tuition_schedules
    ADD COLUMN effective_from DATE,
    ADD COLUMN effective_to   DATE;
UPDATE tuition_schedules SET effective_from = created_at::date WHERE effective_from IS NULL;
ALTER TABLE tuition_schedules
    ALTER COLUMN effective_from SET NOT NULL,
    ADD CONSTRAINT ck_tuition_schedules_dates CHECK (effective_to IS NULL OR effective_to >= effective_from);

-- A constant-expression partial index: every row satisfying the WHERE clause indexes to the same
-- value, so at most one such row can ever exist. The idiom for "singleton row" without pinning a
-- fixed primary key, which effective-dated history can no longer do.
CREATE UNIQUE INDEX uk_tuition_schedules_open ON tuition_schedules ((true)) WHERE effective_to IS NULL;

ALTER TABLE programme_tuition_rates
    ADD COLUMN effective_from DATE,
    ADD COLUMN effective_to   DATE;
UPDATE programme_tuition_rates SET effective_from = created_at::date WHERE effective_from IS NULL;
ALTER TABLE programme_tuition_rates
    ALTER COLUMN effective_from SET NOT NULL,
    DROP CONSTRAINT uk_programme_tuition_rates_programme,
    ADD CONSTRAINT ck_programme_tuition_rates_dates CHECK (effective_to IS NULL OR effective_to >= effective_from);

CREATE UNIQUE INDEX uk_programme_tuition_rates_open
    ON programme_tuition_rates (programme_id) WHERE effective_to IS NULL;
