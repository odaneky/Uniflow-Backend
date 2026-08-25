-- G3: graduation flipped students.status to GRADUATED and recorded nothing else — no conferral
-- date, no GPA or curriculum version snapshot, no honours. degree_awards is that record: the
-- historical evidence a students.status flip alone can never answer once later grades or
-- requirement changes land, the same reasoning grade_revisions and academic_standing_events
-- already established for their own domains.
CREATE TABLE degree_awards (
    id                          UUID          PRIMARY KEY,
    student_id                  UUID          NOT NULL,
    programme_id                UUID          NOT NULL,
    curriculum_version_id       UUID,
    degree_award_label          VARCHAR(50)   NOT NULL,
    conferred_on                DATE          NOT NULL,
    gpa_at_conferral            NUMERIC(4,2),
    credits_earned_at_conferral INTEGER       NOT NULL,
    honours                     VARCHAR(30),
    conferred_by                UUID,
    created_at                  TIMESTAMPTZ   NOT NULL,
    updated_at                  TIMESTAMPTZ   NOT NULL,
    created_by                  VARCHAR(100),
    updated_by                  VARCHAR(100),
    CONSTRAINT fk_degree_awards_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_degree_awards_programme FOREIGN KEY (programme_id) REFERENCES programmes (id),
    CONSTRAINT fk_degree_awards_curriculum_version FOREIGN KEY (curriculum_version_id) REFERENCES curriculum_versions (id),
    CONSTRAINT fk_degree_awards_conferred_by FOREIGN KEY (conferred_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_degree_awards_honours CHECK (honours IN ('CUM_LAUDE', 'MAGNA_CUM_LAUDE', 'SUMMA_CUM_LAUDE'))
);
CREATE INDEX idx_degree_awards_student ON degree_awards (student_id);
