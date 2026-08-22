package com.university.lms.communication.dto;

import com.university.lms.communication.domain.ForumPost;
import java.time.Instant;
import java.util.UUID;

public record ForumPostResponse(
        UUID id,
        UUID topicId,
        UUID parentPostId,
        UUID authorUserId,
        String authorName,
        String body,
        Instant sentAt) {

    public static ForumPostResponse from(ForumPost post, String authorName) {
        return new ForumPostResponse(
                post.getId(),
                post.getTopic().getId(),
                post.getParentPost() == null ? null : post.getParentPost().getId(),
                post.getAuthorUserId(),
                authorName,
                post.visibleBody(),
                post.getSentAt());
    }
}
