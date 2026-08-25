-- ck_curriculum_versions_dates required effective_to strictly after effective_from, so a version
-- published and retired on the same calendar day (a registrar correcting a mistake immediately
-- after publishing, or a fast-moving test) violated the constraint on the retiring UPDATE. A
-- version's effective window is legitimately a single day; only a window running backwards is
-- actually invalid.
ALTER TABLE curriculum_versions DROP CONSTRAINT ck_curriculum_versions_dates;
ALTER TABLE curriculum_versions ADD CONSTRAINT ck_curriculum_versions_dates
    CHECK (effective_to IS NULL OR effective_to >= effective_from);
