-- Single-tenant campus branding. One row; null columns mean "use lms.branding defaults".

CREATE TABLE institution_branding (
    id                  UUID PRIMARY KEY,
    product_name        VARCHAR(80),
    wordmark            VARCHAR(80),
    institution_name    VARCHAR(200),
    welcome_title       VARCHAR(120),
    welcome_subtitle    VARCHAR(400),
    student_cta_label   VARCHAR(80),
    staff_cta_label     VARCHAR(80),
    primary_color       VARCHAR(32),
    accent_color        VARCHAR(32),
    font_sans           VARCHAR(120),
    font_display        VARCHAR(120),
    logo_url            VARCHAR(500),
    favicon_url         VARCHAR(500),
    support_email       VARCHAR(254),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

INSERT INTO institution_branding (id, created_at, updated_at)
VALUES (
    'aaaaaaaa-aaaa-4aaa-8aaa-000000000003',
    NOW(),
    NOW()
);
