-- Grade appeals filed via service requests.

ALTER TABLE grades
    ADD COLUMN under_appeal BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_grades_under_appeal ON grades (under_appeal) WHERE under_appeal = TRUE;
