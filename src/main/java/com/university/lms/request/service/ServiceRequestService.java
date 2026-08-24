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
import com.university.lms.grading.api.GradeAppealActions;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestEvent;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.dto.CompleteServiceRequestRequest;
import com.university.lms.request.dto.CreateServiceRequestRequest;
import com.university.lms.request.dto.DecideServiceRequestRequest;
import com.university.lms.request.dto.ServiceRequestResponse;
import com.university.lms.request.dto.ServiceRequestResponse.EventStep;
import com.university.lms.request.dto.TransitionServiceRequestRequest;
import com.university.lms.request.repository.ServiceRequestEventRepository;
import com.university.lms.request.repository.ServiceRequestRepository;
import com.university.lms.request.service.fulfillment.ServiceRequestFulfillmentService;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServiceRequestService {

    private static final Set<ServiceRequestStatus> CLOSED =
            EnumSet.of(ServiceRequestStatus.COMPLETED, ServiceRequestStatus.DENIED, ServiceRequestStatus.CANCELLED);

    private final ServiceRequestRepository requestRepository;
    private final ServiceRequestEventRepository eventRepository;
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

    public ServiceRequestService(
            ServiceRequestRepository requestRepository,
            ServiceRequestEventRepository eventRepository,
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
            ServiceRequestFulfillmentFailureRecorder fulfillmentFailureRecorder) {
        this.requestRepository = requestRepository;
        this.eventRepository = eventRepository;
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
        if (requestRepository.existsByStudentIdAndRequestTypeAndStatusNotIn(studentId, request.type(), CLOSED)) {
            throw new ResourceAlreadyExistsException(
                    RequestErrorCode.REQUEST_ALREADY_OPEN,
                    "You already have an open " + request.type().displayName() + " request");
        }
        String payloadJson = payloadValidator.validateAndNormalize(request.type(), request.payload(), studentId);
        UUID assignee = workflow.defaultAssignee(request.type(), studentId);
        ServiceRequest saved = requestRepository.save(new ServiceRequest(
                studentId, request.type(), nextReference(request.type()), request.note(), payloadJson, assignee));
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
        return ServiceRequestResponse.from(
                request, studentNumber, studentName, decidedByName, assignedToName, history, payloadMap, events);
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

    private CurrentUser requireStaffReader() {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.ACADEMIC_ADVISOR)
                || caller.hasRole(SecurityRoles.LECTURER)) {
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
