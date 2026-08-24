-- Versions a programme's requirement blocks instead of letting them be edited in place forever.
--
-- Scoped for this pass: a version's requirement blocks become immutable the moment it is
-- published, and re-running a degree audit against a bound version must always return the answer
-- it would have returned at the time. Explicit forking of a published version into a new DRAFT is
-- deliberately deferred — for now, editing a published curriculum is refused outright rather than
-- silently forked, which is the safer failure mode until that workflow exists.
CREATE TABLE curriculum_versions (
    id                UUID          PRIMARY KEY,
    programme_id      UUID          NOT NULL REFERENCES programmes (id),
    catalog_year      VARCHAR(9)    NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    total_credits     INTEGER,
    min_gpa           NUMERIC(3,2),
    residency_credits INTEGER,
    effective_from    DATE          NOT NULL,
    effective_to      DATE,
    published_at      TIMESTAMPTZ,
    retired_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT uk_curriculum_versions        UNIQUE (programme_id, catalog_year),
    CONSTRAINT ck_curriculum_versions_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_curriculum_versions_dates  CHECK (effective_to IS NULL OR effective_to > effective_from)
);
CREATE INDEX idx_curriculum_versions_programme ON curriculum_versions (programme_id);

-- At most one DRAFT and one PUBLISHED version per programme at a time, in this pass — a second
-- concurrent draft or a second live-published catalog year is not yet a supported shape.
CREATE UNIQUE INDEX uk_curriculum_versions_one_draft
    ON curriculum_versions (programme_id) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX uk_curriculum_versions_one_published
    ON curriculum_versions (programme_id) WHERE status = 'PUBLISHED';

ALTER TABLE programme_requirement_blocks
    ADD COLUMN curriculum_version_id UUID REFERENCES curriculum_versions (id);

-- Greenfield: no requirement blocks predate this migration in any real deployment, but a
-- development or test database may already hold rows from before curriculum_versions existed.
-- Give each affected programme a PUBLISHED version dated at its earliest block and re-parent onto
-- it, rather than requiring every environment to be wiped for this migration to apply.
INSERT INTO curriculum_versions (
    id, programme_id, catalog_year, status, effective_from, published_at, created_at, updated_at)
SELECT
    gen_random_uuid(),
    b.programme_id,
    'LEGACY',
    'PUBLISHED',
    CURRENT_DATE,
    now(),
    now(),
    now()
FROM (SELECT DISTINCT programme_id FROM programme_requirement_blocks WHERE curriculum_version_id IS NULL) b;

UPDATE programme_requirement_blocks b
SET curriculum_version_id = cv.id
FROM curriculum_versions cv
WHERE b.curriculum_version_id IS NULL
  AND cv.programme_id = b.programme_id
  AND cv.catalog_year = 'LEGACY';

ALTER TABLE programme_requirement_blocks
    ALTER COLUMN curriculum_version_id SET NOT NULL,
    ADD CONSTRAINT fk_requirement_blocks_version FOREIGN KEY (curriculum_version_id) REFERENCES curriculum_versions (id),
    DROP CONSTRAINT uk_requirement_blocks_programme_name,
    DROP CONSTRAINT fk_requirement_blocks_programme,
    DROP COLUMN programme_id,
    ADD CONSTRAINT uk_requirement_blocks_version_name UNIQUE (curriculum_version_id, name);

DROP INDEX IF EXISTS idx_requirement_blocks_programme;
CREATE INDEX idx_requirement_blocks_version ON programme_requirement_blocks (curriculum_version_id);
