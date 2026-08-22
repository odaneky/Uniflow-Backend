package com.university.lms.communication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.outbox.OutboxWriter;
import com.university.lms.communication.api.ForumAccess;
import com.university.lms.communication.domain.CommunicationErrorCode;
import com.university.lms.communication.domain.ForumPost;
import com.university.lms.communication.domain.ForumTopic;
import com.university.lms.communication.dto.CreateForumPostRequest;
import com.university.lms.communication.dto.CreateForumTopicRequest;
import com.university.lms.communication.dto.ForumPostResponse;
import com.university.lms.communication.dto.ForumTopicResponse;
import com.university.lms.communication.dto.UpdateForumTopicRequest;
import com.university.lms.communication.repository.ForumPostRepository;
import com.university.lms.communication.repository.ForumTopicRepository;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.notification.dispatch.ForumPostCreatedOutboxHandler;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ForumService {

    private final ForumTopicRepository topicRepository;
    private final ForumPostRepository postRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserDirectory userDirectory;
    private final ForumAccess forumAccess;
    private final CourseCatalog courseCatalog;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public ForumService(
            ForumTopicRepository topicRepository,
            ForumPostRepository postRepository,
            CurrentUserProvider currentUserProvider,
            UserDirectory userDirectory,
            ForumAccess forumAccess,
            CourseCatalog courseCatalog,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper) {
        this.topicRepository = topicRepository;
        this.postRepository = postRepository;
        this.currentUserProvider = currentUserProvider;
        this.userDirectory = userDirectory;
        this.forumAccess = forumAccess;
        this.courseCatalog = courseCatalog;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    public PageResponse<ForumTopicResponse> listTopics(UUID courseSectionId, Pageable pageable) {
        CurrentUser caller = currentUserProvider.require();
        forumAccess.assertCanReadForum(caller, courseSectionId);
        return PageResponse.from(
                topicRepository.findByCourseSectionIdOrderByPinnedDescUpdatedAtDesc(courseSectionId, pageable),
                topic -> toTopicResponse(topic));
    }

    @Transactional
    public ForumTopicResponse createTopic(UUID courseSectionId, CreateForumTopicRequest request) {
        CurrentUser caller = currentUserProvider.require();
        forumAccess.assertCanPostForum(caller, courseSectionId);

        ForumTopic topic = topicRepository.save(new ForumTopic(courseSectionId, request.title(), caller.userId()));
        postRepository.save(new ForumPost(topic, caller.userId(), request.body(), null));
        return toTopicResponse(topic);
    }

    public PageResponse<ForumPostResponse> listPosts(UUID topicId, Pageable pageable) {
        ForumTopic topic = requireTopic(topicId);
        forumAccess.assertCanReadForum(currentUserProvider.require(), topic.getCourseSectionId());
        return PageResponse.from(
                postRepository.findByTopicIdAndDeletedAtIsNullOrderBySentAtAscIdAsc(topicId, pageable),
                post -> ForumPostResponse.from(post, displayName(post.getAuthorUserId())));
    }

    @Transactional
    public ForumPostResponse createPost(UUID topicId, CreateForumPostRequest request) {
        CurrentUser caller = currentUserProvider.require();
        ForumTopic topic = requireTopic(topicId);
        forumAccess.assertCanReadForum(caller, topic.getCourseSectionId());

        if (topic.isLocked() && !canModerate(caller, topic.getCourseSectionId())) {
            throw new ForbiddenException(
                    CommunicationErrorCode.FORUM_TOPIC_LOCKED, "This discussion thread is locked");
        }
        forumAccess.assertCanPostForum(caller, topic.getCourseSectionId());

        ForumPost parent = null;
        if (request.parentPostId() != null) {
            parent = postRepository
                    .findById(request.parentPostId())
                    .filter(p -> p.getTopic().getId().equals(topicId))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            CommunicationErrorCode.FORUM_POST_NOT_FOUND,
                            "No forum post exists with id " + request.parentPostId()));
        }

        ForumPost post = postRepository.save(new ForumPost(topic, caller.userId(), request.body(), parent));
        topicRepository.touchUpdatedAt(topicId, Instant.now());
        enqueuePostCreated(post, topic, caller);
        return ForumPostResponse.from(post, caller.fullName());
    }

    @Transactional
    public ForumTopicResponse updateTopic(UUID topicId, UpdateForumTopicRequest request) {
        ForumTopic topic = requireTopic(topicId);
        CurrentUser caller = currentUserProvider.require();
        forumAccess.assertCanModerateForum(caller, topic.getCourseSectionId());

        if (request.pinned() != null) {
            topic.pin(request.pinned());
        }
        if (request.locked() != null) {
            topic.lock(request.locked());
        }
        return toTopicResponse(topic);
    }

    @Transactional
    public void deletePost(UUID postId) {
        CurrentUser caller = currentUserProvider.require();
        ForumPost post = postRepository
                .findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommunicationErrorCode.FORUM_POST_NOT_FOUND,
                        "No forum post exists with id " + postId));
        UUID sectionId = post.getTopic().getCourseSectionId();
        boolean owner = post.getAuthorUserId().equals(caller.userId());
        if (!owner) {
            forumAccess.assertCanModerateForum(caller, sectionId);
        } else {
            forumAccess.assertCanReadForum(caller, sectionId);
        }
        post.softDelete(caller.userId(), Instant.now());
    }

    public ForumTopicResponse getTopic(UUID topicId) {
        ForumTopic topic = requireTopic(topicId);
        forumAccess.assertCanReadForum(currentUserProvider.require(), topic.getCourseSectionId());
        return toTopicResponse(topic);
    }

    private ForumTopic requireTopic(UUID topicId) {
        return topicRepository
                .findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommunicationErrorCode.FORUM_TOPIC_NOT_FOUND,
                        "No forum topic exists with id " + topicId));
    }

    private ForumTopicResponse toTopicResponse(ForumTopic topic) {
        long replies = Math.max(0, postRepository.countByTopicIdAndDeletedAtIsNull(topic.getId()) - 1);
        return ForumTopicResponse.from(topic, displayName(topic.getAuthorUserId()), replies);
    }

    private void enqueuePostCreated(ForumPost post, ForumTopic topic, CurrentUser sender) {
        if (sender.userId().equals(topic.getAuthorUserId())) {
            return;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("postId", post.getId().toString());
        payload.put("topicId", topic.getId().toString());
        payload.put("senderUserId", sender.userId().toString());
        payload.put("topicAuthorUserId", topic.getAuthorUserId().toString());
        payload.put("topicTitle", topic.getTitle());
        payload.put("senderName", sender.fullName());
        courseCatalog
                .findSection(topic.getCourseSectionId())
                .ifPresent(section -> payload.put("courseCode", section.courseCode()));
        outboxWriter.enqueue(
                "ForumPost",
                post.getId(),
                ForumPostCreatedOutboxHandler.EVENT_TYPE,
                payload.toString(),
                "ForumPostCreated:" + post.getId());
    }

    private boolean canModerate(CurrentUser caller, UUID sectionId) {
        try {
            forumAccess.assertCanModerateForum(caller, sectionId);
            return true;
        } catch (ForbiddenException ex) {
            return false;
        }
    }

    private String displayName(UUID userId) {
        return userDirectory.findById(userId).map(UserDirectory.UserSummary::fullName).orElse("Member");
    }
}
