-- File metadata only. Bytes live in an object store; storage_key is the pointer.

CREATE TABLE documents (
    id               UUID         PRIMARY KEY,
    document_type    VARCHAR(40)  NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(150) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,
    storage_provider VARCHAR(30)  NOT NULL,
    owner_user_id    UUID         NOT NULL,
    checksum_sha256  VARCHAR(64),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT uk_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_documents_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT ck_documents_size  CHECK (size_bytes >= 0)
);
CREATE INDEX idx_documents_owner ON documents (owner_user_id);
CREATE INDEX idx_documents_type  ON documents (document_type);
