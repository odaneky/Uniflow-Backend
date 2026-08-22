package com.university.lms.communication.service;

import com.university.lms.communication.api.ForumDirectory;
import com.university.lms.communication.domain.ForumTopic;
import com.university.lms.communication.repository.ForumPostRepository;
import com.university.lms.communication.repository.ForumTopicRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultForumDirectory implements ForumDirectory {

    private final ForumTopicRepository topicRepository;

    public DefaultForumDirectory(ForumTopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    @Override
    public Optional<TopicSummary> findTopicById(UUID topicId) {
        return topicRepository.findById(topicId).map(this::toSummary);
    }

    private TopicSummary toSummary(ForumTopic topic) {
        return new TopicSummary(
                topic.getId(),
                topic.getCourseSectionId(),
                topic.getTitle(),
                topic.getAuthorUserId(),
                topic.isLocked());
    }
}
