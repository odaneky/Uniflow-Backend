-- G8: advising had notes but no way to actually schedule a meeting, or for a student to see one
-- coming up. Advisor-initiated, the same shape advising_notes already uses: an advisor books the
-- slot, a student sees it on their own timetable.
CREATE TABLE advising_appointments (
    id                UUID          PRIMARY KEY,
    student_id        UUID          NOT NULL,
    advisor_user_id   UUID          NOT NULL,
    scheduled_at      TIMESTAMPTZ   NOT NULL,
    duration_minutes  INTEGER       NOT NULL,
    note              VARCHAR(1000),
    cancelled_at      TIMESTAMPTZ,
    cancelled_reason  VARCHAR(500),
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    CONSTRAINT fk_advising_appointments_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_advising_appointments_advisor FOREIGN KEY (advisor_user_id) REFERENCES users (id),
    CONSTRAINT ck_advising_appointments_duration CHECK (duration_minutes > 0)
);
CREATE INDEX idx_advising_appointments_student ON advising_appointments (student_id, scheduled_at);
CREATE INDEX idx_advising_appointments_advisor ON advising_appointments (advisor_user_id, scheduled_at);
