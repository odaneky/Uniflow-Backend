-- G9: majors, minors and specialisations were representable in the schema since V68 (kind,
-- is_primary) but nothing ever wrote a non-primary row — there was no way to actually record a
-- minor or a double major, only the one open primary membership. This prevents a student from
-- somehow ending up with two open records of the same kind in the same programme at once, the
-- secondary-row equivalent of uk_spe_open_primary.
CREATE UNIQUE INDEX uk_spe_open_programme_kind
    ON student_programme_enrolments (student_id, programme_id, kind)
    WHERE ended_on IS NULL;
