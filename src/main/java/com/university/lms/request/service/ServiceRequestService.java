package com.university.lms.request.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.grading.api.GradeAppealActions;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestAttachment;
import com.university.lms.request.domain.ServiceRequestEvent;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.dto.CompleteServiceRequestRequest;
import com.university.lms.request.dto.CreateServiceRequestRequest;
import com.university.lms.request.dto.DecideServiceRequestRequest;
import com.university.lms.request.dto.EscalateServiceRequestRequest;
import com.university.lms.request.dto.ReassignServiceRequestRequest;
import com.university.lms.request.dto.ServiceRequestAttachmentResponse;
import com.university.lms.request.dto.ServiceRequestResponse;
import com.university.lms.request.dto.ServiceRequestResponse.EventStep;
import com.university.lms.request.dto.TransitionServiceRequestRequest;
import com.university.lms.request.repository.ServiceRequestAttachmentRepository;
import com.university.lms.request.repository.ServiceRequestEventRepository;
import com.university.lms.request.repository.ServiceRequestRepository;
import com.university.lms.request.service.fulfillment.ServiceRequestFulfillmentService;
import com.university.lms.student.api.StudentDirectory;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ServiceRequestService {

    private static final Set<ServiceRequestStatus> CLOSED =
            EnumSet.of(ServiceRequestStatus.COMPLETED, ServiceRequestStatus.DENIED, ServiceRequestStatus.CANCELLED);

    /** D9: matches AssessmentService/DocumentService's default cap for a single upload. */
    private static final long MAX_ATTACHMENT_BYTES = 12_582_912L;

    private static final Set<String> ALLOWED_ATTACHMENT_CONTENT_TYPES =
            Set.of("application/pdf", "image/png", "image/jpeg");

    private final ServiceRequestRepository requestRepository;
    private final ServiceRequestEventRepository eventRepository;
    private final ServiceRequestAttachmentRepository attachmentRepository;
    private final StudentDirectory studentDirectory;
    private final UserDirectory userDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final ServiceRequestWorkflow workflow;
    private final ServiceRequestPayloadValidator payloadValidator;
    private final ServiceRequestFulfillmentService fulfillmentService;
    private final ServiceRequestOutboxPublisher outboxPublisher;
    private final GradeAppealActions gradeAppealActions;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;
    private final ServiceRequestFulfillmentFailureRecorder fulfillmentFailureRecorder;
    private final DocumentStore documentStore;

    public ServiceRequestService(
            ServiceRequestRepository requestRepository,
            ServiceRequestEventRepository eventRepository,
            ServiceRequestAttachmentRepository attachmentRepository,
            StudentDirectory studentDirectory,
            UserDirectory userDirectory,
            CurrentUserProvider currentUserProvider,
            ServiceRequestWorkflow workflow,
            ServiceRequestPayloadValidator payloadValidator,
            ServiceRequestFulfillmentService fulfillmentService,
            ServiceRequestOutboxPublisher outboxPublisher,
            GradeAppealActions gradeAppealActions,
            AuditTrail auditTrail,
            ObjectMapper objectMapper,
            ServiceRequestFulfillmentFailureRecorder fulfillmentFailureRecorder,
            DocumentStore documentStore) {
        this.requestRepository = requestRepository;
        this.eventRepository = eventRepository;
        this.attachmentRepository = attachmentRepository;
        this.studentDirectory = studentDirectory;
        this.userDirectory = userDirectory;
        this.currentUserProvider = currentUserProvider;
        this.workflow = workflow;
        this.payloadValidator = payloadValidator;
        this.fulfillmentService = fulfillmentService;
        this.outboxPublisher = outboxPublisher;
        this.gradeAppealActions = gradeAppealActions;
        this.auditTrail = auditTrail;
        this.objectMapper = objectMapper;
        this.fulfillmentFailureRecorder = fulfillmentFailureRecorder;
        this.documentStore = documentStore;
    }

    public List<ServiceRequestResponse> own() {
        return requestRepository.findByStudentIdOrderByUpdatedAtDesc(requireOwnStudent()).stream()
                .map(this::toResponse)
                .toList();
    }

    public ServiceRequestResponse ownById(UUID id) {
        UUID studentId = requireOwnStudent();
        ServiceRequest request = requireOwned(id, studentId);
        return toResponse(request);
    }

    public ServiceRequestResponse staffById(UUID id) {
        requireStaffReader();
        return toResponse(require(id));
    }

    public PageResponse<ServiceRequestResponse> search(
            List<ServiceRequestStatus> statuses,
            ServiceRequestType type,
            Boolean mineOnly,
            String reference,
            Pageable pageable) {
        CurrentUser caller = requireStaffReader();
        UUID assignedTo = Boolean.TRUE.equals(mineOnly) ? caller.userId() : null;
        List<ServiceRequestStatus> effectiveStatuses =
                statuses == null || statuses.isEmpty() ? List.of(ServiceRequestStatus.values()) : statuses;
        return PageResponse.from(
                requestRepository.search(
                        effectiveStatuses, type, assignedTo, referenceLikePattern(reference), pageable),
                this::toResponse);
    }

    @Transactional
    public ServiceRequestResponse createOwn(CreateServiceRequestRequest request) {
        UUID studentId = requireOwnStudent();
        String payloadJson = payloadValidator.validateAndNormalize(request.type(), request.payload(), studentId);
        if (hasOpenRequestOfSameKind(request.type(), studentId, payloadJson)) {
            throw new ResourceAlreadyExistsException(
                    RequestErrorCode.REQUEST_ALREADY_OPEN,
                    "You already have an open " + request.type().displayName() + " request");
        }
        UUID assignee = workflow.defaultAssignee(request.type(), studentId);
        Instant dueAt = Instant.now().plus(request.type().slaDays(), ChronoUnit.DAYS);
        ServiceRequest saved = requestRepository.save(new ServiceRequest(
                studentId, request.type(), nextReference(request.type()), request.note(), payloadJson, assignee, dueAt));
        recordEvent(saved, null, ServiceRequestStatus.SUBMITTED, null, "Submitted", Instant.now());
        auditTrail.record(
                currentUserProvider.require().userId(),
                AuditTrail.Action.SERVICE_REQUEST_CREATED,
                AuditTrail.EntityType.SERVICE_REQUEST,
                saved.getId(),
                saved.getReference() + " " + saved.getRequestType());
        outboxPublisher.publishSubmitted(saved);
        return toResponse(saved);
    }

    /**
     * A grade appeal is scoped to the specific grade it contests, not to the type as a whole — a
     * student appealing grades in two different courses is two independent requests, not a
     * duplicate. Every other type keeps the original one-open-per-type rule.
     */
    private boolean hasOpenRequestOfSameKind(ServiceRequestType type, UUID studentId, String normalizedPayloadJson) {
        if (type == ServiceRequestType.APPEAL) {
            Object gradeId = parsePayload(normalizedPayloadJson).get("gradeId");
            return gradeId != null && requestRepository.existsOpenAppealForGrade(gradeId.toString());
        }
        return requestRepository.existsByStudentIdAndRequestTypeAndStatusNotIn(studentId, type, CLOSED);
    }

    @Transactional
    public ServiceRequestResponse startReview(UUID id, TransitionServiceRequestRequest body) {
        return transitionStaff(id, ServiceRequestStatus.IN_REVIEW, body == null ? null : body.note());
    }

    @Transactional
    public ServiceRequestResponse claim(UUID id) {
        CurrentUser caller = requireStaffReader();
        ServiceRequest request = require(id);
        if (request.getStatus() != ServiceRequestStatus.SUBMITTED
                && request.getStatus() != ServiceRequestStatus.IN_REVIEW) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_INVALID_TRANSITION, "This request cannot be claimed");
        }
        request.assignTo(caller.userId());
        requestRepository.save(request);
        if (request.getStatus() == ServiceRequestStatus.SUBMITTED) {
            return transitionStaff(id, ServiceRequestStatus.IN_REVIEW, "Claimed for review");
        }
        return toResponse(request);
    }

    @Transactional
    public ServiceRequestResponse approve(UUID id, TransitionServiceRequestRequest body) {
        ServiceRequest request = require(id);
        ServiceRequestResponse response = transitionStaff(id, ServiceRequestStatus.APPROVED, noteOf(body));
        if (request.getRequestType() == ServiceRequestType.APPEAL) {
            UUID gradeId = ServiceRequestPayloads.gradeId(request.getPayload());
            if (gradeId != null) {
                gradeAppealActions.openAppeal(gradeId, currentUserProvider.require().userId());
            }
        }
        return response;
    }

    @Transactional
    public ServiceRequestResponse deny(UUID id, TransitionServiceRequestRequest body) {
        return transitionStaff(id, ServiceRequestStatus.DENIED, noteOf(body));
    }

    @Transactional
    public ServiceRequestResponse complete(UUID id, CompleteServiceRequestRequest body) {
        ServiceRequest request = require(id);
        if (body != null && body.deliverableDocumentId() != null) {
            request.attachDeliverable(body.deliverableDocumentId());
        }
        ServiceRequestResponse response =
                transitionStaff(id, ServiceRequestStatus.COMPLETED, body == null ? null : body.note());
        if (request.getFulfilledAt() == null) {
            try {
                fulfillmentService.fulfill(request, currentUserProvider.require());
                request.markFulfilled(Instant.now());
                requestRepository.save(request);
                auditTrail.record(
                        currentUserProvider.require().userId(),
                        AuditTrail.Action.SERVICE_REQUEST_FULFILLED,
                        AuditTrail.EntityType.SERVICE_REQUEST,
                        request.getId(),
                        request.getReference());
                if (request.getDeliverableDocumentId() != null) {
                    outboxPublisher.publishDelivered(request);
                }
            } catch (BusinessException ex) {
                // Recorded in its own transaction — this one is about to roll back because of the
                // exception we are re-throwing, and a save made against this same transaction would
                // roll back with it, exactly as before this fix.
                fulfillmentFailureRecorder.record(request.getId(), ex.getMessage());
                throw ex;
            }
        }
        return response;
    }

    @Transactional
    public ServiceRequestResponse cancelOwn(UUID id, TransitionServiceRequestRequest body) {
        CurrentUser caller = currentUserProvider.require();
        ServiceRequest request = requireOwned(id, requireOwnStudent());
        workflow.assertStudentCancel(caller, request, caller.userId());
        return transition(id, ServiceRequestStatus.CANCELLED, caller.userId(), caller.fullName(), body == null ? null : body.note());
    }

    @Transactional
    public ServiceRequestResponse escalate(UUID id, EscalateServiceRequestRequest body) {
        CurrentUser caller = requireStaffReader();
        ServiceRequest request = require(id);
        try {
            request.escalate(caller.userId(), body.reason(), Instant.now());
        } catch (IllegalStateException ex) {
            throw new BusinessException(RequestErrorCode.REQUEST_CLOSED, ex.getMessage());
        }
        requestRepository.save(request);
        auditTrail.record(
                caller.userId(),
                caller.fullName(),
                AuditTrail.Action.SERVICE_REQUEST_ESCALATED,
                AuditTrail.EntityType.SERVICE_REQUEST,
                request.getId(),
                body.reason());
        return toResponse(request);
    }

    /**
     * D9: staff-to-staff handoff, distinct from {@link #claim} — {@code claim} is a staff member
     * picking up unclaimed work, this is moving already-claimed work off someone's desk. Restricted
     * to the currently assigned staff member (handing off their own queue) or a queue manager
     * (SYSTEM_ADMIN/REGISTRAR); any staff reader could otherwise reassign work that was never
     * theirs to begin with.
     */
    @Transactional
    public ServiceRequestResponse reassign(UUID id, ReassignServiceRequestRequest body) {
        CurrentUser caller = requireStaffReader();
        ServiceRequest request = require(id);
        if (request.getStatus().terminal()) {
            throw new BusinessException(RequestErrorCode.REQUEST_CLOSED, "This request has already been closed");
        }
        boolean isQueueManager =
                caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR);
        boolean isCurrentlyAssigned = caller.userId().equals(request.getAssignedTo());
        if (!isQueueManager && !isCurrentlyAssigned) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED,
                    "Only the assigned staff member or a registrar can reassign this request");
        }
        if (!userDirectory.exists(body.toUserId())) {
            throw new ResourceNotFoundException(
                    RequestErrorCode.REQUEST_REASSIGN_TARGET_NOT_FOUND,
                    "No staff member exists with id " + body.toUserId());
        }
        request.assignTo(body.toUserId());
        requestRepository.save(request);
        auditTrail.record(
                caller.userId(),
                caller.fullName(),
                AuditTrail.Action.SERVICE_REQUEST_REASSIGNED,
                AuditTrail.EntityType.SERVICE_REQUEST,
                request.getId(),
                nameOf(body.toUserId()) + (body.note() == null || body.note().isBlank() ? "" : ": " + body.note()));
        return toResponse(request);
    }

    /**
     * D9: student-submitted evidence for an open request — a document a decision genuinely depends
     * on (a doctor's note for a late-add petition, a death certificate for a bereavement
     * withdrawal), not a general-purpose file drop. Reuses the document module's own storage path
     * exactly as {@code AssessmentService.submitOwn} does, rather than duplicating validation.
     */
    @Transactional
    public ServiceRequestResponse attachOwn(UUID id, MultipartFile file) {
        UUID studentId = requireOwnStudent();
        ServiceRequest request = requireOwned(id, studentId);
        if (request.getStatus().terminal()) {
            throw new BusinessException(RequestErrorCode.REQUEST_CLOSED, "This request has already been closed");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(RequestErrorCode.REQUEST_ATTACHMENT_FILE_REQUIRED, "A file is required");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new BusinessException(RequestErrorCode.REQUEST_ATTACHMENT_TOO_LARGE, "File must be at most 12 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_ATTACHMENT_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_ATTACHMENT_TYPE_NOT_ALLOWED, "Only PDF, PNG or JPEG files are accepted");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(RequestErrorCode.REQUEST_ATTACHMENT_FILE_REQUIRED, "A file is required");
        }
        CurrentUser caller = currentUserProvider.require();
        String originalName = file.getOriginalFilename() == null ? "attachment.bin" : file.getOriginalFilename();
        DocumentStore.StoredFile stored = documentStore.store(caller.userId(), "OTHER", originalName, contentType, bytes);
        attachmentRepository.save(
                new ServiceRequestAttachment(request.getId(), stored.id(), caller.userId(), Instant.now()));
        auditTrail.record(
                caller.userId(),
                caller.fullName(),
                AuditTrail.Action.SERVICE_REQUEST_ATTACHMENT_ADDED,
                AuditTrail.EntityType.SERVICE_REQUEST,
                request.getId(),
                stored.fileName());
        return toResponse(request);
    }

    public record StoredRequestAttachment(String fileName, String contentType, byte[] content) {}

    public StoredRequestAttachment downloadAttachmentOwn(UUID requestId, UUID documentId) {
        UUID studentId = requireOwnStudent();
        requireOwned(requestId, studentId);
        return downloadAttachment(requestId, documentId);
    }

    public StoredRequestAttachment downloadAttachmentForStaff(UUID requestId, UUID documentId) {
        requireStaffReader();
        require(requestId);
        return downloadAttachment(requestId, documentId);
    }

    private StoredRequestAttachment downloadAttachment(UUID requestId, UUID documentId) {
        if (!attachmentRepository.existsByRequestIdAndDocumentId(requestId, documentId)) {
            throw new ResourceNotFoundException(
                    RequestErrorCode.REQUEST_ATTACHMENT_NOT_FOUND, "No attachment exists with id " + documentId);
        }
        DocumentStore.StoredFile meta = documentStore
                .find(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        RequestErrorCode.REQUEST_ATTACHMENT_NOT_FOUND, "Attachment content is unavailable"));
        byte[] bytes = documentStore
                .content(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        RequestErrorCode.REQUEST_ATTACHMENT_NOT_FOUND, "Attachment content is unavailable"));
        return new StoredRequestAttachment(meta.fileName(), meta.contentType(), bytes);
    }

    /** Legacy admin decide — maps coarse statuses to workflow transitions. */
    @Transactional
    public ServiceRequestResponse decide(UUID id, DecideServiceRequestRequest decision) {
        return switch (decision.status()) {
            case IN_REVIEW -> startReview(id, new TransitionServiceRequestRequest(decision.note()));
            case APPROVED -> approve(id, new TransitionServiceRequestRequest(decision.note()));
            case DENIED -> deny(id, new TransitionServiceRequestRequest(decision.note()));
            case COMPLETED -> complete(id, new CompleteServiceRequestRequest(decision.note(), null));
            case SUBMITTED -> throw new BusinessException(
                    RequestErrorCode.REQUEST_INVALID_DECISION, "A decision cannot return a request to submitted");
            case CANCELLED -> throw new BusinessException(
                    RequestErrorCode.REQUEST_INVALID_DECISION, "Use the cancel endpoint to cancel a request");
        };
    }

    private ServiceRequestResponse transitionStaff(UUID id, ServiceRequestStatus target, String note) {
        CurrentUser caller = requireStaffReader();
        ServiceRequest request = require(id);
        workflow.assertStaffAction(caller, request, target);
        return transition(id, target, caller.userId(), caller.fullName(), note);
    }

    private ServiceRequestResponse transition(
            UUID id, ServiceRequestStatus target, UUID actorUserId, String actorLabel, String note) {
        ServiceRequest request = require(id);
        ServiceRequestStatus from = request.getStatus();
        if (from.terminal()) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_ALREADY_DECIDED, "This request has already been closed");
        }
        workflow.assertTransition(request, target);
        Instant now = Instant.now();
        request.transitionTo(target, actorUserId, note, now);
        if (target == ServiceRequestStatus.IN_REVIEW && request.getAssignedTo() == null) {
            request.assignTo(actorUserId);
        }
        requestRepository.save(request);
        ServiceRequestEvent event = recordEvent(request, from, target, actorUserId, note, now);
        auditTrail.record(
                actorUserId,
                actorLabel,
                AuditTrail.Action.SERVICE_REQUEST_TRANSITIONED,
                AuditTrail.EntityType.SERVICE_REQUEST,
                request.getId(),
                from + " -> " + target);
        outboxPublisher.publishStatusChanged(request, from, actorUserId, event.getId());
        return toResponse(request);
    }

    private ServiceRequestEvent recordEvent(
            ServiceRequest request,
            ServiceRequestStatus from,
            ServiceRequestStatus to,
            UUID actorUserId,
            String note,
            Instant at) {
        return eventRepository.save(new ServiceRequestEvent(request.getId(), from, to, actorUserId, note, at));
    }

    private ServiceRequestResponse toResponse(ServiceRequest request) {
        StudentDirectory.StudentSummary student = studentDirectory.findById(request.getStudentId()).orElse(null);
        String studentNumber = student == null ? null : student.studentNumber();
        String studentName = student == null
                ? null
                : userDirectory.findById(student.userId()).map(UserDirectory.UserSummary::fullName).orElse(null);
        String decidedByName = nameOf(request.getDecidedBy());
        String assignedToName = nameOf(request.getAssignedTo());
        String escalatedByName = nameOf(request.getEscalatedBy());
        List<ServiceRequestEvent> history =
                eventRepository.findByRequestIdOrderByCreatedAtAsc(request.getId());
        List<EventStep> events = history.stream()
                .map(event -> new EventStep(
                        event.getId(),
                        event.getFromStatus(),
                        event.getToStatus(),
                        event.getActorUserId(),
                        nameOf(event.getActorUserId()),
                        event.getNote(),
                        event.getCreatedAt()))
                .toList();
        Map<String, Object> payloadMap = parsePayload(request.getPayload());
        List<ServiceRequestAttachmentResponse> attachments = attachmentRepository
                .findByRequestIdOrderByUploadedAtAsc(request.getId())
                .stream()
                .map(this::toAttachmentResponse)
                .toList();
        return ServiceRequestResponse.from(
                request,
                studentNumber,
                studentName,
                decidedByName,
                assignedToName,
                escalatedByName,
                history,
                payloadMap,
                events,
                attachments);
    }

    private ServiceRequestAttachmentResponse toAttachmentResponse(ServiceRequestAttachment attachment) {
        DocumentStore.StoredFile meta = documentStore.find(attachment.getDocumentId()).orElse(null);
        return new ServiceRequestAttachmentResponse(
                attachment.getDocumentId(),
                meta == null ? null : meta.fileName(),
                meta == null ? null : meta.contentType(),
                meta == null ? 0 : meta.sizeBytes(),
                attachment.getUploadedBy(),
                nameOf(attachment.getUploadedBy()),
                attachment.getUploadedAt());
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String nameOf(UUID userId) {
        return userId == null
                ? null
                : userDirectory.findById(userId).map(UserDirectory.UserSummary::fullName).orElse(null);
    }

    private ServiceRequest require(UUID id) {
        return requestRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        RequestErrorCode.REQUEST_NOT_FOUND, "No request exists with id " + id));
    }

    private ServiceRequest requireOwned(UUID id, UUID studentId) {
        return requestRepository
                .findById(id)
                .filter(row -> row.getStudentId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        RequestErrorCode.REQUEST_NOT_FOUND, "No request exists with id " + id));
    }

    private String nextReference(ServiceRequestType type) {
        for (int i = 0; i < 8; i++) {
            String candidate = type.referencePrefix() + "-"
                    + String.format("%05d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000));
            if (!requestRepository.existsByReference(candidate)) {
                return candidate;
            }
        }
        return type.referencePrefix() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID requireOwnStudent() {
        CurrentUser caller = currentUserProvider.require();
        return studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
    }

    /**
     * A6: the coarse gate before {@link ServiceRequestWorkflow}'s per-type fine check. Widened to
     * also accept {@code FINANCIAL_AID_OFFICER} — without this, the SAP_APPEAL branch added to
     * {@code assertCanReview}/{@code assertCanComplete} would be unreachable, since a caller with
     * only that role would already be refused here before ever reaching the finer check.
     */
    private CurrentUser requireStaffReader() {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.ACADEMIC_ADVISOR)
                || caller.hasRole(SecurityRoles.LECTURER)
                || caller.hasRole(SecurityRoles.FINANCIAL_AID_OFFICER)) {
            return caller;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
    }

    private static String noteOf(TransitionServiceRequestRequest body) {
        return body == null ? null : body.note();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String referenceLikePattern(String reference) {
        String trimmed = blankToNull(reference);
        if (trimmed == null) {
            return null;
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
