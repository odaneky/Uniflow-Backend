-- Optional quiz/exam presentation metadata for the student detail page.

ALTER TABLE assessments
    ADD COLUMN duration_minutes INTEGER,
    ADD COLUMN pass_mark_percent NUMERIC(5, 2);

ALTER TABLE assessments
    ADD CONSTRAINT ck_assessments_duration CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    ADD CONSTRAINT ck_assessments_pass_mark CHECK (
        pass_mark_percent IS NULL
        OR (pass_mark_percent >= 0 AND pass_mark_percent <= 100)
    );
