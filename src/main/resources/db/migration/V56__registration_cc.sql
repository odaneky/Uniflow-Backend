-- Community-college registration: waitlist offers, section approvals, checkout idempotency.

ALTER TABLE enrollments
    ADD COLUMN waitlist_offer_expires_at TIMESTAMPTZ;

ALTER TABLE course_sections
    ADD COLUMN requires_approval BOOLEAN NOT NULL DEFAULT FALSE;

-- Safe client retries for registration checkout (same pattern as V42 message idempotency).
CREATE TABLE enrollment_checkout_idempotency (
    id               UUID         PRIMARY KEY,
    student_id       UUID         NOT NULL,
    idempotency_key  VARCHAR(200) NOT NULL,
    checkout_batch_id UUID        NOT NULL,
    response_json    JSONB        NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_enrollment_checkout_idempotency UNIQUE (student_id, idempotency_key),
    CONSTRAINT fk_enrollment_checkout_idempotency_student FOREIGN KEY (student_id) REFERENCES students (id)
);

CREATE INDEX idx_enrollment_checkout_idempotency_student ON enrollment_checkout_idempotency (student_id);
