-- Academic structure: Faculty -> Department -> Programme, and the calendar.

CREATE TABLE faculties (
    id           UUID         PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    dean_user_id UUID,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    CONSTRAINT uk_faculties_code UNIQUE (code),
    CONSTRAINT fk_faculties_dean FOREIGN KEY (dean_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE departments (
    id           UUID         PRIMARY KEY,
    faculty_id   UUID         NOT NULL,
    code         VARCHAR(20)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    head_user_id UUID,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    CONSTRAINT uk_departments_code   UNIQUE (code),
    CONSTRAINT fk_departments_faculty FOREIGN KEY (faculty_id) REFERENCES faculties (id),
    CONSTRAINT fk_departments_head    FOREIGN KEY (head_user_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_departments_faculty ON departments (faculty_id);

CREATE TABLE programmes (
    id             UUID         PRIMARY KEY,
    department_id  UUID         NOT NULL,
    code           VARCHAR(20)  NOT NULL,
    name           VARCHAR(200) NOT NULL,
    degree_award   VARCHAR(100) NOT NULL,
    total_credits  INTEGER      NOT NULL,
    duration_years INTEGER      NOT NULL,
    active         BOOLEAN      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    CONSTRAINT uk_programmes_code       UNIQUE (code),
    CONSTRAINT fk_programmes_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT ck_programmes_credits    CHECK (total_credits > 0),
    CONSTRAINT ck_programmes_duration   CHECK (duration_years > 0)
);
CREATE INDEX idx_programmes_department ON programmes (department_id);

CREATE TABLE academic_years (
    id         UUID        PRIMARY KEY,
    code       VARCHAR(20) NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uk_academic_years_code  UNIQUE (code),
    CONSTRAINT ck_academic_years_dates CHECK (end_date > start_date)
);

CREATE TABLE academic_terms (
    id                     UUID         PRIMARY KEY,
    academic_year_id       UUID         NOT NULL,
    name                   VARCHAR(100) NOT NULL,
    term_type              VARCHAR(30)  NOT NULL,
    sequence_number        INTEGER      NOT NULL,
    start_date             DATE         NOT NULL,
    end_date               DATE         NOT NULL,
    registration_opens_at  TIMESTAMPTZ,
    registration_closes_at TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    created_by             VARCHAR(100),
    updated_by             VARCHAR(100),
    CONSTRAINT uk_academic_terms_year_sequence UNIQUE (academic_year_id, sequence_number),
    CONSTRAINT fk_academic_terms_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT ck_academic_terms_dates  CHECK (end_date > start_date),
    -- Either both ends of the registration window are set, or neither is; a half-open window
    -- would be ambiguous exactly when it matters most.
    CONSTRAINT ck_academic_terms_window CHECK (
        (registration_opens_at IS NULL AND registration_closes_at IS NULL)
        OR (registration_opens_at IS NOT NULL AND registration_closes_at IS NOT NULL
            AND registration_closes_at > registration_opens_at)
    )
);
CREATE INDEX idx_academic_terms_year ON academic_terms (academic_year_id);
