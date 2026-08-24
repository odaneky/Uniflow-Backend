-- Per-programme admissions application form field definitions.

CREATE TABLE programme_application_forms (
    programme_id UUID         PRIMARY KEY,
    fields         JSONB        NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    CONSTRAINT fk_programme_application_forms_programme
        FOREIGN KEY (programme_id) REFERENCES programmes (id) ON DELETE CASCADE
);
