package com.university.lms.communication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.outbox.OutboxWriter;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.communication.api.CommsRateLimiter;
import com.university.lms.communication.api.MessagingPolicy;
import com.university.lms.communication.policy.InMemoryCommsRateLimiter;
import com.university.lms.communication.domain.Announcement;
import com.university.lms.communication.domain.AnnouncementRead;
import com.university.lms.communication.domain.CommunicationErrorCode;
import com.university.lms.communication.domain.Conversation;
import com.university.lms.communication.domain.ConversationParticipant;
import com.university.lms.communication.domain.Message;
import com.university.lms.communication.dto.AnnouncementResponse;
import com.university.lms.communication.dto.ConversationComplianceExport;
import com.university.lms.communication.dto.ConversationSummaryResponse;
import com.university.lms.communication.dto.CreateAnnouncementRequest;
import com.university.lms.communication.dto.CreateConversationRequest;
import com.university.lms.communication.dto.CreateMessageRequest;
import com.university.lms.communication.dto.MessageResponse;
import com.university.lms.communication.dto.StartConversationResult;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.communication.repository.AnnouncementReadRepository;
import com.university.lms.communication.repository.AnnouncementRepository;
import com.university.lms.communication.repository.ConversationParticipantRepository;
import com.university.lms.communication.repository.ConversationRepository;
import com.university.lms.communication.repository.MessageRepository;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.notification.dispatch.AnnouncementPublishedOutboxHandler;
import com.university.lms.notification.dispatch.MessageSentOutboxHandler;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommunicationService {

    private static final UUID NONE = UUID.fromString("00000000-0000-4000-8000-000000000000");
    private static final Instant EPOCH = Instant.EPOCH;

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserDirectory userDirectory;
    private final StudentDirectory studentDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final MessagingPolicy messagingPolicy;
    private final CommsRateLimiter commsRateLimiter;
    private final DocumentStore documentStore;
    private final AuditTrail auditTrail;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public CommunicationService(
            AnnouncementRepository announcementRepository,
            AnnouncementReadRepository announcementReadRepository,
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            CurrentUserProvider currentUserProvider,
            UserDirectory userDirectory,
            StudentDirectory studentDirectory,
            EnrollmentDirectory enrollmentDirectory,
            MessagingPolicy messagingPolicy,
            CommsRateLimiter commsRateLimiter,
            DocumentStore documentStore,
            AuditTrail auditTrail,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper) {
        this.announcementRepository = announcementRepository;
        this.announcementReadRepository = announcementReadRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.currentUserProvider = currentUserProvider;
        this.userDirectory = userDirectory;
        this.studentDirectory = studentDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.messagingPolicy = messagingPolicy;
        this.commsRateLimiter = commsRateLimiter;
        this.documentStore = documentStore;
        this.auditTrail = auditTrail;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    public List<AnnouncementResponse> ownAnnouncements() {
        CurrentUser caller = currentUserProvider.require();
        UUID programmeId = NONE;
        List<UUID> sectionIds = List.of(NONE);
        UUID studentId = studentDirectory.studentIdOfUser(caller.userId()).orElse(null);
        List<Announcement> visible;
        if (studentId != null) {
            programmeId = studentDirectory.findById(studentId).map(StudentDirectory.StudentSummary::programmeId).orElse(NONE);
            List<UUID> accessible = enrollmentDirectory.accessibleSectionIds(studentId);
            sectionIds = accessible.isEmpty() ? List.of(NONE) : accessible;
            visible = announcementRepository.findVisibleTo(
                    com.university.lms.communication.domain.AnnouncementAudience.UNIVERSITY_WIDE,
                    com.university.lms.communication.domain.AnnouncementAudience.PROGRAMME,
                    com.university.lms.communication.domain.AnnouncementAudience.COURSE_SECTION,
                    programmeId,
                    sectionIds);
        } else if (caller.isStaff()) {
            visible = announcementRepository.findByAudienceAndPublishedAtIsNotNullOrderByPublishedAtDesc(
                    com.university.lms.communication.domain.AnnouncementAudience.UNIVERSITY_WIDE);
        } else {
            visible = List.of();
        }
        return mapWithReadState(visible, caller.userId());
    }

    @Transactional
    public void markAnnouncementRead(UUID announcementId) {
        CurrentUser caller = currentUserProvider.require();
        boolean visible = ownAnnouncements().stream().anyMatch(a -> a.id().equals(announcementId));
        if (!visible) {
            throw new ResourceNotFoundException(
                    CommunicationErrorCode.ANNOUNCEMENT_NOT_FOUND,
                    "No announcement exists with id " + announcementId);
        }
        if (!announcementReadRepository.existsByAnnouncementIdAndUserId(announcementId, caller.userId())) {
            announcementReadRepository.save(new AnnouncementRead(announcementId, caller.userId(), Instant.now()));
        }
    }

    private List<AnnouncementResponse> mapWithReadState(List<Announcement> announcements, UUID userId) {
        if (announcements.isEmpty()) {
            return List.of();
        }
        Set<UUID> announcementIds =
                announcements.stream().map(Announcement::getId).collect(Collectors.toSet());
        Set<UUID> readIds = announcementReadRepository.findReadAnnouncementIdsForUser(userId, announcementIds);
        return announcements.stream()
                .map(a -> AnnouncementResponse.from(a, readIds.contains(a.getId())))
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
        Announcement saved = announcementRepository.save(announcement);
        if (saved.isPublished()) {
            enqueueAnnouncementPublished(saved);
        }
        return AnnouncementResponse.from(saved, false);
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
        enqueueAnnouncementPublished(announcement);
        return AnnouncementResponse.from(announcement, false);
    }

    private void enqueueAnnouncementPublished(Announcement announcement) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("announcementId", announcement.getId().toString());
        outboxWriter.enqueue(
                "Announcement",
                announcement.getId(),
                AnnouncementPublishedOutboxHandler.EVENT_TYPE,
                payload.toString(),
                "AnnouncementPublished:" + announcement.getId());
    }

    public PageResponse<ConversationSummaryResponse> ownConversations(Pageable pageable) {
        UUID userId = currentUserProvider.require().userId();
        return PageResponse.from(conversationRepository.findAllForParticipant(userId, pageable), this::toSummary);
    }

    public PageResponse<MessageResponse> ownMessages(UUID conversationId, Pageable pageable) {
        messagingPolicy.assertCanReadConversation(currentUserProvider.require(), conversationId);
        return PageResponse.from(
                messageRepository.findByConversationIdAndDeletedAtIsNullOrderBySentAtDesc(conversationId, pageable),
                message -> MessageResponse.from(message, displayName(message.getSenderUserId())));
    }

    public long ownUnreadConversationCount() {
        UUID userId = currentUserProvider.require().userId();
        return messageRepository.countTotalUnreadForUser(userId, EPOCH);
    }

    @Transactional
    public void markConversationRead(UUID conversationId) {
        CurrentUser caller = currentUserProvider.require();
        messagingPolicy.assertCanReadConversation(caller, conversationId);
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, caller.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommunicationErrorCode.CONVERSATION_PARTICIPANT_NOT_FOUND,
                        "You are not a participant in this conversation"));
        participant.markReadAt(Instant.now());
        participantRepository.save(participant);
    }

    @Transactional
    public StartConversationResult startConversation(CreateConversationRequest request) {
        CurrentUser caller = currentUserProvider.require();
        commsRateLimiter.check(InMemoryCommsRateLimiter.CONVERSATION_CREATE, caller.userId());
        Set<UUID> participants = new HashSet<>(request.participantUserIds());
        participants.add(caller.userId());
        messagingPolicy.assertCanStartConversation(caller, participants, request.courseSectionId());

        List<Conversation> existing =
                conversationRepository.findByExactParticipants(request.courseSectionId(), participants, participants.size());
        if (!existing.isEmpty()) {
            return new StartConversationResult(false, toSummary(existing.getFirst()));
        }

        Conversation conversation = new Conversation(request.subject(), caller.userId());
        if (request.courseSectionId() != null) {
            conversation.attachToSection(request.courseSectionId());
        }
        conversationRepository.save(conversation);

        for (UUID userId : participants) {
            participantRepository.save(new ConversationParticipant(conversation, userId));
        }

        if (request.firstMessage() != null && !request.firstMessage().isBlank()) {
            sendInternal(conversation, caller, request.firstMessage(), null, null);
        }
        return new StartConversationResult(true, toSummary(conversation));
    }

    @Transactional
    public SendMessageResult send(UUID conversationId, CreateMessageRequest request, String idempotencyKey) {
        CurrentUser caller = currentUserProvider.require();
        messagingPolicy.assertCanSendMessage(caller, conversationId);
        commsRateLimiter.check(InMemoryCommsRateLimiter.MESSAGE_SEND, caller.userId());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = messageRepository.findByConversationIdAndIdempotencyKey(conversationId, idempotencyKey);
            if (existing.isPresent()) {
                Message message = existing.get();
                return new SendMessageResult(false, MessageResponse.from(message, displayName(message.getSenderUserId())));
            }
        }

        Conversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommunicationErrorCode.CONVERSATION_NOT_FOUND,
                        "No conversation exists with id " + conversationId));

        MessageResponse created = sendInternal(conversation, caller, request.body(), idempotencyKey, request.documentId());
        return new SendMessageResult(true, created);
    }

    public ConversationComplianceExport exportConversationCompliance(UUID conversationId) {
        CurrentUser caller = currentUserProvider.require();
        if (!caller.hasRole(SecurityRoles.SYSTEM_ADMIN)) {
            throw new ForbiddenException(
                    com.university.lms.common.exception.CommonErrorCode.ACCESS_DENIED,
                    "You do not have permission to access this record");
        }
        Conversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommunicationErrorCode.CONVERSATION_NOT_FOUND,
                        "No conversation exists with id " + conversationId));

        auditTrail.record(
                caller.userId(),
                caller.fullName(),
                AuditTrail.Action.MESSAGE_THREAD_ACCESSED,
                AuditTrail.EntityType.CONVERSATION,
                conversationId,
                "Compliance export");

        List<ConversationComplianceExport.ParticipantRow> participants = participantRepository
                .findByConversationId(conversationId)
                .stream()
                .map(p -> new ConversationComplianceExport.ParticipantRow(
                        p.getUserId(), displayName(p.getUserId())))
                .toList();

        List<ConversationComplianceExport.MessageRow> messages = messageRepository
                .findByConversationIdOrderBySentAtAscIdAsc(conversationId)
                .stream()
                .map(m -> new ConversationComplianceExport.MessageRow(
                        m.getId(),
                        m.getSenderUserId(),
                        displayName(m.getSenderUserId()),
                        m.getSentAt(),
                        m.isDeleted() ? "[removed]" : m.getBody(),
                        m.getDocumentId(),
                        m.isDeleted(),
                        m.getDeletedAt()))
                .toList();

        return new ConversationComplianceExport(
                conversation.getId(),
                conversation.getSubject(),
                conversation.getCourseSectionId(),
                Instant.now(),
                participants,
                messages);
    }

    private MessageResponse sendInternal(
            Conversation conversation, CurrentUser caller, String body, String idempotencyKey, UUID documentId) {
        Message message = idempotencyKey == null || idempotencyKey.isBlank()
                ? new Message(conversation, caller.userId(), body)
                : new Message(conversation, caller.userId(), body, idempotencyKey);
        if (documentId != null) {
            documentStore
                    .find(documentId)
                    .filter(doc -> doc.ownerUserId().equals(caller.userId()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            CommunicationErrorCode.MESSAGE_ATTACHMENT_NOT_FOUND,
                            "No attachment exists with id " + documentId));
            message.attachDocument(documentId);
        }
        messageRepository.save(message);
        enqueueMessageSent(message, caller);
        return MessageResponse.from(message, caller.fullName());
    }

    private void enqueueMessageSent(Message message, CurrentUser sender) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("messageId", message.getId().toString());
        payload.put("conversationId", message.getConversation().getId().toString());
        payload.put("senderUserId", sender.userId().toString());
        payload.put("senderName", sender.fullName());
        outboxWriter.enqueue(
                "Message",
                message.getId(),
                MessageSentOutboxHandler.EVENT_TYPE,
                payload.toString(),
                "MessageSent:" + message.getId());
    }

    private ConversationSummaryResponse toSummary(Conversation conversation) {
        UUID userId = currentUserProvider.require().userId();
        Message last = messageRepository
                .findFirstByConversationIdAndDeletedAtIsNullOrderBySentAtDesc(conversation.getId())
                .orElse(null);
        String preview = last == null ? null : last.visibleBody();
        UUID senderId = last == null ? null : last.getSenderUserId();
        Instant lastReadAt = participantRepository
                .findByConversationIdAndUserId(conversation.getId(), userId)
                .map(ConversationParticipant::getLastReadAt)
                .orElse(null);
        long unread = messageRepository.countUnreadInConversation(conversation.getId(), userId, lastReadAt, EPOCH);
        return new ConversationSummaryResponse(
                conversation.getId(),
                conversation.getSubject(),
                conversation.getCourseSectionId(),
                conversation.getUpdatedAt(),
                preview,
                senderId,
                senderId == null ? null : displayName(senderId),
                unread);
    }

    private String displayName(UUID userId) {
        return userDirectory.findById(userId).map(UserDirectory.UserSummary::fullName).orElse("Member");
    }

    private static void requireStaff(CurrentUser caller) {
        if (!caller.isStaff()) {
            throw new com.university.lms.common.exception.ForbiddenException(
                    com.university.lms.common.exception.CommonErrorCode.ACCESS_DENIED,
                    "You do not have permission to access this record");
        }
    }

    public record SendMessageResult(boolean created, MessageResponse message) {}
}
