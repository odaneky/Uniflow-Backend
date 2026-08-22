-- Student ledger (metadata only; no payment-processor integration) and a uniqueness
-- guarantee for per-assessment grades.

CREATE UNIQUE INDEX uk_grades_student_section_assessment
    ON grades (student_id, course_section_id, assessment_id)
    WHERE assessment_id IS NOT NULL;

CREATE TABLE student_accounts (
    id          UUID         PRIMARY KEY,
    student_id  UUID         NOT NULL,
    currency    VARCHAR(3)   NOT NULL,
    due_on      DATE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    CONSTRAINT uk_student_accounts_student UNIQUE (student_id),
    CONSTRAINT fk_student_accounts_student FOREIGN KEY (student_id) REFERENCES students (id)
);

CREATE TABLE account_entries (
    id           UUID          PRIMARY KEY,
    account_id   UUID          NOT NULL,
    entry_type   VARCHAR(20)   NOT NULL,
    amount       NUMERIC(12,2) NOT NULL,
    description  VARCHAR(500)  NOT NULL,
    occurred_at  TIMESTAMPTZ   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    CONSTRAINT fk_account_entries_account FOREIGN KEY (account_id) REFERENCES student_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_account_entries_amount CHECK (amount <> 0)
);
CREATE INDEX idx_account_entries_account ON account_entries (account_id);
