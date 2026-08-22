-- Module attempt number: which sit of a course this enrolment represents (retake = 2+).
ALTER TABLE enrollments
    ADD COLUMN IF NOT EXISTS attempt_number INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN enrollments.attempt_number IS
    'Sit number for this student on the underlying course (1 = first attempt; increments on retake).';
