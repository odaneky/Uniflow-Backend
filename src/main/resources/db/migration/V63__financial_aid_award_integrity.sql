-- Financial-aid awards had no uniqueness and no status constraint. packageAwards() unconditionally
-- inserted a new PELL/INSTITUTIONAL award on every call, so a client retry after a timeout — or a
-- double submit of the packaging form — created duplicate awards, and a duplicate ACCEPTED award
-- could be disbursed twice. One award per student, per term, per type is the actual policy; this
-- constraint is what makes it true rather than merely intended.

CREATE UNIQUE INDEX uk_financial_aid_awards_student_term_type
    ON financial_aid_awards (student_id, academic_term_id, award_type);

ALTER TABLE financial_aid_awards
    ADD CONSTRAINT ck_financial_aid_awards_status
        CHECK (status IN ('OFFERED', 'ACCEPTED', 'DECLINED', 'DISBURSED'));
