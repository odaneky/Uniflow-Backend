-- G5: an attached application document (transcript, ID, etc.) had no verification state at all —
-- admissions staff could see one was uploaded but had no way to record that they had checked it.
ALTER TABLE application_documents
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN verified_by UUID,
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD COLUMN rejection_reason VARCHAR(500);

ALTER TABLE application_documents ADD CONSTRAINT ck_application_documents_status
    CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED'));
