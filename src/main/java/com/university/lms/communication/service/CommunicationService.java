package com.university.lms.communication.service;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.communication.domain.Announcement;
import com.university.lms.communication.domain.CommunicationErrorCode;
import com.university.lms.communication.domain.Conversation;
import com.university.lms.communication.domain.ConversationParticipant;
import com.university.lms.communication.domain.Message;
import com.university.lms.communication.dto.AnnouncementResponse;
import com.university.lms.communication.dto.ConversationSummaryResponse;
import com.university.lms.communication.dto.CreateAnnouncementRequest;
import com.university.lms.communication.dto.CreateConversationRequest;
import com.university.lms.communication.dto.CreateMessageRequest;
import com.university.lms.communication.dto.MessageResponse;
import com.university.lms.communication.repository.AnnouncementRepository;
import com.university.lms.communication.repository.ConversationParticipantRepository;
import com.university.lms.communication.repository.ConversationRepository;
import com.university.lms.communication.repository.MessageRepository;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommunicationService {

    private static final UUID NONE = UUID.fromString("00000000-0000-4000-8000-000000000000");

    private final AnnouncementRepository announcementRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserDirectory userDirectory;
    private final StudentDirectory studentDirectory;
    private final EnrollmentDirectory enrollmentDirectory;

    public CommunicationService(
            AnnouncementRepository announcementRepository,
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            CurrentUserProvider currentUserProvider,
            UserDirectory userDirectory,
            StudentDirectory studentDirectory,
            EnrollmentDirectory enrollmentDirectory) {
        this.announcementRepository = announcementRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.currentUserProvider = currentUserProvider;
        this.userDirectory = userDirectory;
        this.studentDirectory = studentDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
    }

    public List<AnnouncementResponse> ownAnnouncements() {
        CurrentUser caller = currentUserProvider.require();
        UUID programmeId = NONE;
        List<UUID> sectionIds = List.of(NONE);
        UUID studentId = studentDirectory.studentIdOfUser(caller.userId()).orElse(null);
        if (studentId != null) {
            programmeId = studentDirectory.findById(studentId).map(StudentDirectory.StudentSummary::programmeId).orElse(NONE);
            List<UUID> accessible = enrollmentDirectory.accessibleSectionIds(studentId);
            sectionIds = accessible.isEmpty() ? List.of(NONE) : accessible;
        } else if (caller.isStaff()) {
            return announcementRepository
                    .findByAudienceAndPublishedAtIsNotNullOrderByPublishedAtDesc(
                            com.university.lms.communication.domain.AnnouncementAudience.UNIVERSITY_WIDE)
                    .stream()
                    .map(AnnouncementResponse::from)
                    .toList();
        }
        return announcementRepository
                .findVisibleTo(
                        com.university.lms.communication.domain.AnnouncementAudience.UNIVERSITY_WIDE,
                        com.university.lms.communication.domain.AnnouncementAudience.PROGRAMME,
                        com.university.lms.communication.domain.AnnouncementAudience.COURSE_SECTION,
                        programmeId,
                        sectionIds)
                .stream()
                .map(AnnouncementResponse::from)
                .toList();
    }

    @Transactional
    public AnnouncementResponse createAnnouncement(CreateAnnouncementRequest request) {
        CurrentUser caller = currentUserProvider.require();
        requireStaff(caller);
        Announcement announcement = new Announcement(
                request.title(), request.body(), request.audience(), request.audienceRefId(), caller.userId());
        if (Boolean.TRUE.equals(request.publish())) {
            announcement.publish(Instant.now());
        }
        return AnnouncementResponse.from(announcementRepository.save(announcement));
    }

    @Transactional
    public AnnouncementResponse publishAnnouncement(UUID announcementId) {
        requireStaff(currentUserProvider.require());
        Announcement announcement = announcementRepository
                .findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommunicationErrorCode.ANNOUNCEMENT_NOT_FOUND,
                        "No announcement exists with id " + announcementId));
        announcement.publish(Instant.now());
        return AnnouncementResponse.from(announcement);
    }

    public PageResponse<ConversationSummaryResponse> ownConversations(Pageable pageable) {
        UUID userId = currentUserProvider.require().userId();
        return PageResponse.from(conversationRepository.findAllForParticipant(userId, pageable), this::toSummary);
    }

    public PageResponse<MessageResponse> ownMessages(UUID conversationId, Pageable pageable) {
        requireParticipant(conversationId);
        return PageResponse.from(
                messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable),
                message -> MessageResponse.from(message, displayName(message.getSenderUserId())));
    }

    @Transactional
    public ConversationSummaryResponse startConversation(CreateConversationRequest request) {
        CurrentUser caller = currentUserProvider.require();
        Conversation conversation = new Conversation(request.subject(), caller.userId());
        if (request.courseSectionId() != null) {
            conversation.attachToSection(request.courseSectionId());
        }
        conversationRepository.save(conversation);

        Set<UUID> participants = new HashSet<>(request.participantUserIds());
        participants.add(caller.userId());
        for (UUID userId : participants) {
            if (!userDirectory.exists(userId)) {
                continue;
            }
            participantRepository.save(new ConversationParticipant(conversation, userId));
        }

        if (request.firstMessage() != null && !request.firstMessage().isBlank()) {
            messageRepository.save(new Message(conversation, caller.userId(), request.firstMessage()));
        }
        return toSummary(conversation);
    }

    @Transactional
    public MessageResponse send(UUID conversationId, CreateMessageRequest request) {
        requireParticipant(conversationId);
        Conversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommunicationErrorCode.CONVERSATION_NOT_FOUND,
                        "No conversation exists with id " + conversationId));
        CurrentUser caller = currentUserProvider.require();
        Message message = messageRepository.save(new Message(conversation, caller.userId(), request.body()));
        return MessageResponse.from(message, caller.fullName());
    }

    private ConversationSummaryResponse toSummary(Conversation conversation) {
        Message last = messageRepository
                .findFirstByConversationIdOrderBySentAtDesc(conversation.getId())
                .orElse(null);
        String preview = last == null ? null : last.getBody();
        UUID senderId = last == null ? null : last.getSenderUserId();
        return new ConversationSummaryResponse(
                conversation.getId(),
                conversation.getSubject(),
                conversation.getCourseSectionId(),
                conversation.getUpdatedAt(),
                preview,
                senderId,
                senderId == null ? null : displayName(senderId));
    }

    private String displayName(UUID userId) {
        return userDirectory.findById(userId).map(UserDirectory.UserSummary::fullName).orElse("Member");
    }

    private void requireParticipant(UUID conversationId) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)) {
            return;
        }
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, caller.userId())) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }

    private static void requireStaff(CurrentUser caller) {
        if (!caller.isStaff()) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }
}
