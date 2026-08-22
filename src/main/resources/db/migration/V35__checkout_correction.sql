-- Students may undo a confirmed checkout while registration/add-drop is still open,
-- for a university-configured number of hours after that checkout (default 48).

ALTER TABLE institution_academic_policies
    ADD COLUMN checkout_correction_hours INTEGER NOT NULL DEFAULT 48;

ALTER TABLE institution_academic_policies
    ADD CONSTRAINT ck_institution_academic_policy_correction_hours
        CHECK (checkout_correction_hours BETWEEN 0 AND 168);

ALTER TABLE enrollments
    ADD COLUMN checkout_batch_id UUID;

CREATE INDEX idx_enrollments_checkout_batch
    ON enrollments (student_id, checkout_batch_id)
    WHERE checkout_batch_id IS NOT NULL;
