-- G5: multi-reviewer scoring. Each reviewer scores an application independently; scores are shown
-- together for the registrar's own decision, not aggregated into an automatic one — that policy
-- choice (weighting, thresholds, tie-breaking) is a separate decision this does not make.
CREATE TABLE application_scores (
    application_id   UUID NOT NULL REFERENCES applications (id),
    reviewer_user_id UUID NOT NULL,
    score            INTEGER NOT NULL,
    comment          VARCHAR(2000),
    scored_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (application_id, reviewer_user_id),
    CONSTRAINT ck_application_scores_range CHECK (score BETWEEN 1 AND 5)
);

CREATE INDEX idx_application_scores_application ON application_scores (application_id);
