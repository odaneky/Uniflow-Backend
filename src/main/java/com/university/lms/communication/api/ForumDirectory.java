package com.university.lms.communication.api;

import java.util.Optional;
import java.util.UUID;

public interface ForumDirectory {

    record TopicSummary(
            UUID id,
            UUID courseSectionId,
            String title,
            UUID authorUserId,
            boolean locked) {}

    Optional<TopicSummary> findTopicById(UUID topicId);
}
