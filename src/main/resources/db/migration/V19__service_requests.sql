-- Student service requests (transcript, withdrawal, verification, appeal, graduation).
-- Status and decision live on the row; no document bytes are stored here.

CREATE TABLE service_requests (
    id             UUID         PRIMARY KEY,
    student_id     UUID         NOT NULL,
    request_type   VARCHAR(30)  NOT NULL,
    status         VARCHAR(30)  NOT NULL,
    reference      VARCHAR(20)  NOT NULL,
    note           VARCHAR(2000),
    decision_note  VARCHAR(2000),
    decided_by     UUID,
    decided_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    CONSTRAINT uk_service_requests_reference UNIQUE (reference),
    CONSTRAINT fk_service_requests_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT ck_service_requests_type CHECK (
        request_type IN ('TRANSCRIPT', 'WITHDRAWAL', 'VERIFICATION', 'APPEAL', 'GRADUATION')
    ),
    CONSTRAINT ck_service_requests_status CHECK (
        status IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED', 'COMPLETED', 'DENIED')
    )
);
CREATE INDEX idx_service_requests_student ON service_requests (student_id);
CREATE INDEX idx_service_requests_status ON service_requests (status);
