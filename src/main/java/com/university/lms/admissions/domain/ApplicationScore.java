package com.university.lms.admissions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * G5: one reviewer's independent score for an application.
 *
 * <p>Deliberately not aggregated into the decision here — {@code decide} still requires a
 * registrar's own judgement. Weighting, thresholds and tie-breaking are a policy choice for
 * whoever owns the admissions process, not something to default silently into the code.
 */
@Entity
@Table(name = "application_scores")
@IdClass(ApplicationScore.ApplicationScoreId.class)
@Getter
public class ApplicationScore {

    @Id
    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Id
    @Column(name = "reviewer_user_id", nullable = false)
    private UUID reviewerUserId;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    protected ApplicationScore() {
        // for JPA
    }

    public ApplicationScore(UUID applicationId, UUID reviewerUserId, int score, String comment) {
        this.applicationId = applicationId;
        this.reviewerUserId = reviewerUserId;
        this.scoredAt = Instant.now();
        update(score, comment);
    }

    /** A reviewer revising their own score replaces it — one score per reviewer, not a history. */
    public void update(int score, String comment) {
        this.score = score;
        this.comment = comment;
        this.scoredAt = Instant.now();
    }

    public record ApplicationScoreId(UUID applicationId, UUID reviewerUserId) implements Serializable {}
}
