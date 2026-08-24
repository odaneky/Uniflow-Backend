-- Append-only history of every grade award and change, plus term-close locking.
--
-- Unlike audit_events (V27's no-FK rule), this keeps its foreign key: it is domain history that
-- happens to live next to the audit trail, not the audit trail itself, and grades is not a table
-- the trail contends with under load.
--
-- No UPDATE or DELETE is ever issued against this table — GradeRevisionRepository exposes only
-- save() and finders, never JpaRepository's full surface — so the schema does not need a trigger
-- to enforce it.
CREATE TABLE grade_revisions (
    id                 UUID          PRIMARY KEY,
    grade_id           UUID          NOT NULL,
    revision_number    INTEGER       NOT NULL,
    before_percentage  NUMERIC(5,2),
    before_letter      VARCHAR(5),
    before_grade_point NUMERIC(4,2),
    after_percentage   NUMERIC(5,2)  NOT NULL,
    after_letter       VARCHAR(5)    NOT NULL,
    after_grade_point  NUMERIC(4,2)  NOT NULL,
    reason             VARCHAR(1000) NOT NULL,
    changed_by         UUID          NOT NULL,
    approved_by        UUID,
    changed_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_grade_revisions_grade FOREIGN KEY (grade_id) REFERENCES grades (id),
    CONSTRAINT uk_grade_revisions UNIQUE (grade_id, revision_number)
);
CREATE INDEX idx_grade_revisions_grade ON grade_revisions (grade_id);

-- Set at term close (C6/C7); a locked grade refuses further revision until routed through a
-- review workflow where the approver differs from whoever changed it.
ALTER TABLE grades
    ADD COLUMN locked_at TIMESTAMPTZ,
    ADD COLUMN locked_by UUID;
