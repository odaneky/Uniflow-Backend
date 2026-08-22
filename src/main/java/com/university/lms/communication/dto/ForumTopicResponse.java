package com.university.lms.communication.dto;

import com.university.lms.communication.domain.ForumPost;
import com.university.lms.communication.domain.ForumTopic;
import java.time.Instant;
import java.util.UUID;

public record ForumTopicResponse(
        UUID id,
        UUID courseSectionId,
        String title,
        UUID authorUserId,
        String authorName,
        boolean pinned,
        boolean locked,
        long replyCount,
        Instant updatedAt,
        Instant createdAt) {

    public static ForumTopicResponse from(ForumTopic topic, String authorName, long replyCount) {
        return new ForumTopicResponse(
                topic.getId(),
                topic.getCourseSectionId(),
                topic.getTitle(),
                topic.getAuthorUserId(),
                authorName,
                topic.isPinned(),
                topic.isLocked(),
                replyCount,
                topic.getUpdatedAt(),
                topic.getCreatedAt());
    }
}
