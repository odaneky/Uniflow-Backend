-- An exam can be cancelled, and a cancelled exam is not a deleted one.
--
-- Academic records stay auditable: a paper that was scheduled, published, and then withdrawn is
-- part of what happened to a cohort, and students who planned around it will ask why. Deleting the
-- row erases the question along with the answer. The same reasoning already keeps dropped
-- enrolments and cancelled sections in place rather than removing them.

ALTER TABLE exam_sittings
    ADD COLUMN status           VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    ADD COLUMN cancelled_reason VARCHAR(500);

ALTER TABLE exam_sittings
    ADD CONSTRAINT ck_exam_sittings_status CHECK (status IN ('SCHEDULED', 'CANCELLED'));

-- A cancelled sitting no longer holds its hall, so double-booking checks must skip it — otherwise
-- a withdrawn exam blocks its own replacement from being scheduled in the same room.
CREATE INDEX idx_exam_sittings_active ON exam_sittings (room, starts_at) WHERE status = 'SCHEDULED';
