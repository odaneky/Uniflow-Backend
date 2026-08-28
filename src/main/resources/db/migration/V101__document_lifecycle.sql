-- F4: retention expiry and virus-scan status on stored documents. NOT_SCANNED is the honest
-- default for every existing row and any deployment with no scanner configured (NoopVirusScanner)
-- -- distinct from CLEAN, which only a real scan may claim.
ALTER TABLE documents
    ADD COLUMN expires_at        TIMESTAMPTZ,
    ADD COLUMN virus_scan_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SCANNED';

ALTER TABLE documents
    ADD CONSTRAINT ck_documents_virus_scan_status
        CHECK (virus_scan_status IN ('NOT_SCANNED', 'CLEAN', 'INFECTED'));

-- Partial: only documents actually carrying an expiry are ever queried by the retention sweeper.
CREATE INDEX idx_documents_expires_at ON documents (expires_at)
    WHERE expires_at IS NOT NULL;
