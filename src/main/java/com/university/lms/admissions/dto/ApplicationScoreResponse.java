package com.university.lms.admissions.dto;

import com.university.lms.admissions.domain.ApplicationScore;
import java.time.Instant;
import java.util.UUID;

public record ApplicationScoreResponse(
        UUID reviewerUserId, String reviewerName, int score, String comment, Instant scoredAt) {

    public static ApplicationScoreResponse from(ApplicationScore score, String reviewerName) {
        return new ApplicationScoreResponse(
                score.getReviewerUserId(), reviewerName, score.getScore(), score.getComment(), score.getScoredAt());
    }
}
