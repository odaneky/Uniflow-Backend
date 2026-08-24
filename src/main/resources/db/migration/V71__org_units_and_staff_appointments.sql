-- A4: the missing link between a staff member and where they actually work. Nothing today ties a
-- LECTURER token to the department that owns the section they teach, or a FACULTY_ADMIN to the
-- faculty they administer — every non-student role is treated as unrestricted staff access
-- everywhere (CurrentUser.isStaff()). This is additive only: faculties and departments are
-- untouched, and nothing existing reads these tables yet. Org-scoped authorization that actually
-- consults staff_appointments is separate work (A5), once this exists to consult.
--
-- org_units is self-referencing rather than reusing the Faculty/Department pair because a
-- registrar's office, a bursar's office or a financial aid office is a real organizational unit
-- staff are appointed to, and none of those is a "Department" in the Faculty -> Department ->
-- Programme sense that table already means.
CREATE TABLE org_units (
    id                  UUID         PRIMARY KEY,
    parent_org_unit_id  UUID         REFERENCES org_units (id),
    code                VARCHAR(30)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    unit_type           VARCHAR(30)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT uk_org_units_code UNIQUE (code),
    CONSTRAINT ck_org_units_type CHECK (
        unit_type IN ('INSTITUTION', 'FACULTY', 'DEPARTMENT', 'ADMINISTRATIVE_OFFICE')
    )
);
CREATE INDEX idx_org_units_parent ON org_units (parent_org_unit_id);

-- Staff attributes identity deliberately does not carry (see identity.domain.User's own javadoc on
-- why it stays login-only): rank, contract type, FTE.
CREATE TABLE employees (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id),
    employee_number VARCHAR(30),
    rank            VARCHAR(50),
    contract_type   VARCHAR(30)  NOT NULL,
    fte             NUMERIC(3,2) NOT NULL DEFAULT 1.00,
    hired_on        DATE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uk_employees_user UNIQUE (user_id),
    CONSTRAINT ck_employees_contract CHECK (
        contract_type IN ('FULL_TIME', 'PART_TIME', 'ADJUNCT', 'CONTRACT')
    ),
    CONSTRAINT ck_employees_fte CHECK (fte > 0 AND fte <= 1)
);

-- The scope source: which org unit, in which role, for how long. A person may hold more than one
-- open appointment at once (a lecturer who also advises), so this deliberately has no uniqueness
-- constraint narrower than the row itself.
CREATE TABLE staff_appointments (
    id            UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id),
    org_unit_id   UUID        NOT NULL REFERENCES org_units (id),
    role          VARCHAR(50) NOT NULL,
    valid_from    DATE        NOT NULL,
    valid_to      DATE,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    CONSTRAINT ck_staff_appointments_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX idx_staff_appointments_user ON staff_appointments (user_id);
CREATE INDEX idx_staff_appointments_org_unit ON staff_appointments (org_unit_id);
