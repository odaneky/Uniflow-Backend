-- Community college pilot: residency and term-scoped ledger references.

ALTER TABLE students
    ADD COLUMN residency_classification VARCHAR(30) NOT NULL DEFAULT 'IN_DISTRICT';

ALTER TABLE account_entries
    ADD COLUMN academic_term_id UUID;

CREATE INDEX idx_account_entries_term ON account_entries (account_id, academic_term_id);

COMMENT ON COLUMN students.residency_classification IS 'IN_DISTRICT, OUT_OF_DISTRICT, OUT_OF_STATE — drives tuition tier';
