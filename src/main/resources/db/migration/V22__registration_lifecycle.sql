-- Semester registration lifecycle: add/drop calendar, section meeting times (so a
-- timetable can be generated after enrolment), and idempotent billing references.

ALTER TABLE academic_terms
    ADD COLUMN add_drop_opens_at  TIMESTAMPTZ,
    ADD COLUMN add_drop_closes_at TIMESTAMPTZ,
    ADD COLUMN tuition_due_on     DATE;

ALTER TABLE academic_terms
    ADD CONSTRAINT ck_academic_terms_add_drop CHECK (
        (add_drop_opens_at IS NULL AND add_drop_closes_at IS NULL)
        OR (
            add_drop_opens_at IS NOT NULL
            AND add_drop_closes_at IS NOT NULL
            AND add_drop_closes_at > add_drop_opens_at
        )
    );

-- day_of_week: 1 = Monday … 5 = Friday (ISO).
CREATE TABLE section_meetings (
    id            UUID         PRIMARY KEY,
    section_id    UUID         NOT NULL,
    day_of_week   SMALLINT     NOT NULL,
    start_time    TIME         NOT NULL,
    end_time      TIME         NOT NULL,
    room          VARCHAR(40)  NOT NULL,
    session_type  VARCHAR(20)  NOT NULL,
    position      INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    CONSTRAINT uk_section_meetings UNIQUE (section_id, position),
    CONSTRAINT fk_section_meetings_section FOREIGN KEY (section_id) REFERENCES course_sections (id) ON DELETE CASCADE,
    CONSTRAINT ck_section_meetings_day CHECK (day_of_week BETWEEN 1 AND 5),
    CONSTRAINT ck_section_meetings_span CHECK (end_time > start_time)
);
CREATE INDEX idx_section_meetings_section ON section_meetings (section_id);

ALTER TABLE account_entries
    ADD COLUMN reference VARCHAR(80);

CREATE UNIQUE INDEX uk_account_entries_account_reference
    ON account_entries (account_id, reference)
    WHERE reference IS NOT NULL;
