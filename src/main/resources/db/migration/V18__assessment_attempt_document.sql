-- Links an attempt to the uploaded artefact in the document register. Bytes stay in the object
-- store; this column is only the metadata id. Nullable so an in-progress attempt can exist before
-- a file is attached.

ALTER TABLE assessment_attempts
    ADD COLUMN document_id UUID;

ALTER TABLE assessment_attempts
    ADD CONSTRAINT fk_assessment_attempts_document
        FOREIGN KEY (document_id) REFERENCES documents (id);

CREATE INDEX idx_assessment_attempts_document ON assessment_attempts (document_id);
