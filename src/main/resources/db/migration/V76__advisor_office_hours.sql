-- G8: office hours were written to every advisee's row (StudentService.updateOwnAdvisorOfficeHours
-- looped every student assigned to the caller) instead of stored once per advisor. A newly assigned
-- student saw blank hours until the advisor happened to re-post them, and N rows carried the same
-- fact redundantly. Office hours are advisor-level data, not student-level data.
CREATE TABLE advisor_office_hours (
    id               UUID           PRIMARY KEY,
    advisor_user_id  UUID           NOT NULL,
    office_hours     VARCHAR(200),
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    CONSTRAINT uk_advisor_office_hours_advisor UNIQUE (advisor_user_id)
);

ALTER TABLE students DROP COLUMN advisor_office_hours;
