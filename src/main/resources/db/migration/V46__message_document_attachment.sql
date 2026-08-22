-- Optional file attachment on direct messages (Phase 4).

ALTER TABLE messages ADD COLUMN document_id UUID;

ALTER TABLE messages
    ADD CONSTRAINT fk_messages_document FOREIGN KEY (document_id) REFERENCES documents (id);

CREATE INDEX idx_messages_document ON messages (document_id) WHERE document_id IS NOT NULL;
