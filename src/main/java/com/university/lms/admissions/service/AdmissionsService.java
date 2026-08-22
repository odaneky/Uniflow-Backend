package com.university.lms.admissions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.admissions.domain.AdmissionDecision;
import com.university.lms.admissions.domain.AdmissionsErrorCode;
import com.university.lms.admissions.domain.Application;
import com.university.lms.admissions.domain.ApplicationDocument;
import com.university.lms.admissions.domain.ApplicationEvent;
import com.university.lms.admissions.domain.ApplicationStatus;
import com.university.lms.admissions.dto.ApplicationResponse;
import com.university.lms.admissions.dto.AttachApplicationDocumentRequest;
import com.university.lms.admissions.dto.CreateApplicationRequest;
import com.university.lms.admissions.dto.DecideApplicationRequest;
import com.university.lms.admissions.dto.MatriculateApplicationRequest;
import com.university.lms.admissions.dto.TransitionApplicationRequest;
import com.university.lms.admissions.dto.UpdateApplicationRequest;
import com.university.lms.admissions.repository.ApplicationDocumentRepository;
import com.university.lms.admissions.repository.ApplicationEventRepository;
import com.university.lms.admissions.repository.ApplicationRepository;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.identity.domain.IdentityErrorCode;
import com.university.lms.identity.dto.ProvisionIdentityRequest;
import com.university.lms.identity.dto.UserResponse;
import com.university.lms.identity.service.IdentityProvisioningService;
import com.university.lms.identity.spi.IdentityProvider;
import com.university.lms.student.domain.StudentErrorCode;
import com.university.lms.student.dto.CreateStudentRequest;
import com.university.lms.student.dto.ProvisionStudentRequest;
import com.university.lms.student.dto.StudentResponse;
import com.university.lms.student.service.StudentProvisioningService;
import com.university.lms.student.service.StudentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdmissionsService {

    private static final Set<ApplicationStatus> CLOSED =
            EnumSet.of(ApplicationStatus.DENIED, ApplicationStatus.MATRICULATED);

    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final AcademicStructure academicStructure;
    private final DocumentStore documentStore;
    private final UserDirectory userDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AdmissionsWorkflow workflow;
    private final StudentService studentService;
    private final StudentProvisioningService studentProvisioningService;
    private final IdentityProvisioningService identityProvisioningService;
    private final IdentityProvider identityProvider;
    private final AuditTrail auditTrail;
    private final ObjectMapper objectMapper;

    public AdmissionsService(
            ApplicationRepository applicationRepository,
            ApplicationEventRepository eventRepository,
            ApplicationDocumentRepository documentRepository,
            AcademicStructure academicStructure,
            DocumentStore documentStore,
            UserDirectory userDirectory,
            CurrentUserProvider currentUserProvider,
            AdmissionsWorkflow workflow,
            StudentService studentService,
            StudentProvisioningService studentProvisioningService,
            IdentityProvisioningService identityProvisioningService,
            IdentityProvider identityProvider,
            AuditTrail auditTrail,
            ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.documentRepository = documentRepository;
        this.academicStructure = academicStructure;
        this.documentStore = documentStore;
        this.userDirectory = userDirectory;
        this.currentUserProvider = currentUserProvider;
        this.workflow = workflow;
        this.studentService = studentService;
        this.studentProvisioningService = studentProvisioningService;
        this.identityProvisioningService = identityProvisioningService;
        this.identityProvider = identityProvider;
        this.auditTrail = auditTrail;
        this.objectMapper = objectMapper;
    }

    public ApplicationResponse findById(UUID id) {
        return toResponse(require(id));
    }

    public PageResponse<ApplicationResponse> queue(
            List<ApplicationStatus> statuses, Boolean mineOnly, String reference, Pageable pageable) {
        CurrentUser caller = requireStaffReader();
        UUID assignedTo = Boolean.TRUE.equals(mineOnly) ? caller.userId() : null;
        List<ApplicationStatus> effectiveStatuses =
                statuses == null || statuses.isEmpty() ? defaultQueueStatuses() : statuses;
        return PageResponse.from(
                applicationRepository.search(effectiveStatuses, assignedTo, blankToNull(reference), pageable),
                this::toResponse);
    }

    @Transactional
    public ApplicationResponse createDraft(CreateApplicationRequest request) {
        validateProgrammeAndTerm(request.programmeId(), request.academicTermId());
        assertNoOpenApplication(request.applicantEmail(), request.programmeId(), request.academicTermId());

        String payloadJson = serializePayload(request.payload());
        Application saved = applicationRepository.save(new Application(
                request.applicantEmail(),
                request.applicantName(),
                request.programmeId(),
                request.academicTermId(),
                nextReference(),
                payloadJson));
        recordEvent(saved, null, ApplicationStatus.DRAFT, null, "Draft created", Instant.now());
        auditTrail.record(
                actorIdOrNull(),
                AuditTrail.Action.APPLICATION_CREATED,
                AuditTrail.EntityType.APPLICATION,
                saved.getId(),
                saved.getReference());

        if (Boolean.TRUE.equals(request.submit())) {
            return submit(saved.getId());
        }
        return toResponse(saved);
    }

    @Transactional
    public ApplicationResponse update(UUID id, UpdateApplicationRequest request) {
        Application application = require(id);
        if (application.getStatus() != ApplicationStatus.DRAFT) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_NOT_EDITABLE,
                    "Only draft applications can be edited");
        }
        application.updateDraft(
                request.applicantEmail(),
                request.applicantName(),
                request.payload() == null ? null : serializePayload(request.payload()));
        return toResponse(applicationRepository.save(application));
    }

    @Transactional
    public ApplicationResponse submit(UUID id) {
        Application application = require(id);
        if (application.getStatus() != ApplicationStatus.DRAFT) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_INVALID_TRANSITION,
                    "Only draft applications can be submitted");
        }
        return transition(application, ApplicationStatus.SUBMITTED, null, null, "Submitted");
    }

    @Transactional
    public ApplicationResponse staffClaim(UUID id) {
        CurrentUser caller = requireStaffReader();
        Application application = require(id);
        if (application.getStatus() != ApplicationStatus.SUBMITTED
                && application.getStatus() != ApplicationStatus.IN_REVIEW) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_INVALID_TRANSITION, "This application cannot be claimed");
        }
        application.assignTo(caller.userId());
        applicationRepository.save(application);
        if (application.getStatus() == ApplicationStatus.SUBMITTED) {
            return transition(application, ApplicationStatus.IN_REVIEW, caller.userId(), caller.fullName(), "Claimed for review");
        }
        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse review(UUID id, TransitionApplicationRequest body) {
        CurrentUser caller = requireStaffReader();
        Application application = require(id);
        workflow.assertStaffAction(caller, ApplicationStatus.IN_REVIEW);
        if (application.getStatus() == ApplicationStatus.SUBMITTED) {
            return transition(
                    application,
                    ApplicationStatus.IN_REVIEW,
                    caller.userId(),
                    caller.fullName(),
                    noteOf(body, "Review started"));
        }
        if (application.getStatus() != ApplicationStatus.IN_REVIEW) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_INVALID_TRANSITION,
                    "Only submitted or in-review applications can be reviewed");
        }
        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse decide(UUID id, DecideApplicationRequest body) {
        CurrentUser caller = requireStaffReader();
        Application application = require(id);
        ApplicationStatus target = workflow.targetFor(body.decision());
        workflow.assertStaffAction(caller, target);
        if (target == ApplicationStatus.ADMITTED && body.depositAmount() != null) {
            application.setDepositAmount(body.depositAmount());
        }
        return transition(application, target, caller.userId(), caller.fullName(), body.note());
    }

    @Transactional
    public ApplicationResponse recordDeposit(UUID id) {
        CurrentUser caller = requireStaffReader();
        Application application = require(id);
        workflow.assertStaffAction(caller, ApplicationStatus.ADMITTED);
        if (application.getStatus() != ApplicationStatus.ADMITTED) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_INVALID_TRANSITION,
                    "Deposit can only be recorded for admitted applications");
        }
        if (application.getDepositPaidAt() != null) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_DEPOSIT_ALREADY_RECORDED,
                    "Deposit has already been recorded for this application");
        }
        if (application.getDepositAmount() != null
                && application.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
            application.recordDepositPaid(Instant.now());
            applicationRepository.save(application);
            auditTrail.record(
                    caller.userId(),
                    caller.fullName(),
                    AuditTrail.Action.APPLICATION_TRANSITIONED,
                    AuditTrail.EntityType.APPLICATION,
                    application.getId(),
                    "Deposit recorded");
            return toResponse(application);
        }
        application.recordDepositPaid(Instant.now());
        applicationRepository.save(application);
        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse matriculate(UUID id, MatriculateApplicationRequest body) {
        CurrentUser caller = requireStaffReader();
        Application application = require(id);
        workflow.assertStaffAction(caller, ApplicationStatus.MATRICULATED);
        if (application.getStatus() != ApplicationStatus.ADMITTED) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_INVALID_TRANSITION,
                    "Only admitted applications can be matriculated");
        }
        if (application.getStudentId() != null) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_ALREADY_MATRICULATED,
                    "This application is already linked to a student record");
        }
        if (application.getDepositAmount() != null
                && application.getDepositAmount().compareTo(BigDecimal.ZERO) > 0
                && application.getDepositPaidAt() == null) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_DEPOSIT_REQUIRED,
                    "Deposit must be recorded before matriculation");
        }

        LocalDate admissionDate = body.admissionDate() == null ? LocalDate.now() : body.admissionDate();
        StudentResponse student = createStudentForApplication(application, body.studentNumber(), admissionDate);
        application.linkStudent(student.id());
        ApplicationResponse response = transition(
                application, ApplicationStatus.MATRICULATED, caller.userId(), caller.fullName(), "Matriculated");
        auditTrail.record(
                caller.userId(),
                caller.fullName(),
                AuditTrail.Action.APPLICATION_MATRICULATED,
                AuditTrail.EntityType.APPLICATION,
                application.getId(),
                "Student " + body.studentNumber());
        return response;
    }

    @Transactional
    public ApplicationResponse attachDocument(UUID id, AttachApplicationDocumentRequest body) {
        Application application = require(id);
        if (application.getStatus() != ApplicationStatus.DRAFT
                && application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_NOT_EDITABLE,
                    "Documents can only be attached to draft or submitted applications");
        }
        if (documentStore.find(body.documentId()).isEmpty()) {
            throw new ResourceNotFoundException(
                    AdmissionsErrorCode.APPLICATION_DOCUMENT_NOT_FOUND,
                    "No document exists with id " + body.documentId());
        }
        if (!documentRepository.existsByApplicationIdAndDocumentId(id, body.documentId())) {
            documentRepository.save(new ApplicationDocument(id, body.documentId()));
        }
        return toResponse(application);
    }

    private StudentResponse createStudentForApplication(
            Application application, String studentNumber, LocalDate admissionDate) {
        try {
            if (identityProvider.isAvailable()) {
                UserResponse user = provisionIdentity(application, studentNumber);
                return studentService.create(new CreateStudentRequest(
                        user.id(), studentNumber, application.getProgrammeId(), admissionDate, null));
            }
            return studentProvisioningService.provision(new ProvisionStudentRequest(
                    studentNumber, application.getProgrammeId(), admissionDate, null));
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == IdentityErrorCode.IDENTITY_PROVIDER_UNAVAILABLE
                    || ex.getErrorCode() == StudentErrorCode.STUDENT_USER_NOT_FOUND) {
                throw new BusinessException(
                        AdmissionsErrorCode.APPLICATION_MATRICULATION_FAILED,
                        ex.getMessage(),
                        ex);
            }
            throw ex;
        } catch (ResourceNotFoundException ex) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_MATRICULATION_FAILED, ex.getMessage(), ex);
        }
    }

    private UserResponse provisionIdentity(Application application, String studentNumber) {
        String[] parts = splitName(application.getApplicantName());
        return identityProvisioningService.provision(new ProvisionIdentityRequest(
                studentNumber,
                application.getApplicantEmail(),
                parts[0],
                parts[1],
                studentNumber,
                Set.of(SecurityRoles.STUDENT)));
    }

    private ApplicationResponse transition(
            Application application,
            ApplicationStatus target,
            UUID actorUserId,
            String actorLabel,
            String note) {
        if (application.getStatus().terminal()) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_ALREADY_CLOSED,
                    "This application has already been closed");
        }
        workflow.assertTransition(application, target);
        Instant now = Instant.now();
        ApplicationStatus from = application.getStatus();
        application.transitionTo(target, actorUserId, note, now);
        if (target == ApplicationStatus.IN_REVIEW && application.getAssignedTo() == null && actorUserId != null) {
            application.assignTo(actorUserId);
        }
        applicationRepository.save(application);
        recordEvent(application, from, target, actorUserId, note, now);
        if (actorUserId != null) {
            auditTrail.record(
                    actorUserId,
                    actorLabel,
                    AuditTrail.Action.APPLICATION_TRANSITIONED,
                    AuditTrail.EntityType.APPLICATION,
                    application.getId(),
                    from + " -> " + target);
        }
        return toResponse(application);
    }

    private ApplicationEvent recordEvent(
            Application application,
            ApplicationStatus from,
            ApplicationStatus to,
            UUID actorUserId,
            String note,
            Instant at) {
        return eventRepository.save(new ApplicationEvent(application.getId(), from, to, actorUserId, note, at));
    }

    private ApplicationResponse toResponse(Application application) {
        List<ApplicationEvent> history = eventRepository.findByApplicationIdOrderByCreatedAtAsc(application.getId());
        List<UUID> documentIds = documentRepository.findByApplicationId(application.getId()).stream()
                .map(ApplicationDocument::getDocumentId)
                .toList();
        return ApplicationResponse.from(
                application,
                nameOf(application.getAssignedTo()),
                nameOf(application.getDecidedBy()),
                parsePayload(application.getPayload()),
                documentIds,
                ApplicationResponse.eventSteps(history, this::nameOf));
    }

    private void validateProgrammeAndTerm(UUID programmeId, UUID academicTermId) {
        if (!academicStructure.programmeExists(programmeId)) {
            throw new ResourceNotFoundException(
                    AdmissionsErrorCode.APPLICATION_PROGRAMME_NOT_FOUND,
                    "No programme exists with id " + programmeId);
        }
        if (academicStructure.findTerm(academicTermId, Instant.now()).isEmpty()) {
            throw new ResourceNotFoundException(
                    AdmissionsErrorCode.APPLICATION_TERM_NOT_FOUND,
                    "No academic term exists with id " + academicTermId);
        }
    }

    private void assertNoOpenApplication(String email, UUID programmeId, UUID academicTermId) {
        if (applicationRepository.existsByApplicantEmailIgnoreCaseAndProgrammeIdAndAcademicTermIdAndStatusNotIn(
                email.trim(), programmeId, academicTermId, CLOSED)) {
            throw new ResourceAlreadyExistsException(
                    AdmissionsErrorCode.APPLICATION_ALREADY_OPEN,
                    "An open application already exists for this programme and term");
        }
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_INVALID_TRANSITION, "Application payload is invalid");
        }
    }

    private String nextReference() {
        for (int i = 0; i < 8; i++) {
            String candidate = "APP-" + String.format("%05d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000));
            if (!applicationRepository.existsByReference(candidate)) {
                return candidate;
            }
        }
        return "APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Application require(UUID id) {
        return applicationRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AdmissionsErrorCode.APPLICATION_NOT_FOUND, "No application exists with id " + id));
    }

    private CurrentUser requireStaffReader() {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return caller;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to access admissions");
    }

    private String nameOf(UUID userId) {
        return userId == null
                ? null
                : userDirectory.findById(userId).map(UserDirectory.UserSummary::fullName).orElse(null);
    }

    private UUID actorIdOrNull() {
        return currentUserProvider.find().map(CurrentUser::userId).orElse(null);
    }

    private static List<ApplicationStatus> defaultQueueStatuses() {
        return List.of(
                ApplicationStatus.SUBMITTED,
                ApplicationStatus.IN_REVIEW,
                ApplicationStatus.WAITLISTED,
                ApplicationStatus.ADMITTED);
    }

    private static String noteOf(TransitionApplicationRequest body, String fallback) {
        if (body == null || body.note() == null || body.note().isBlank()) {
            return fallback;
        }
        return body.note();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[] {trimmed, "Applicant"};
        }
        return new String[] {trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }
}
