package com.university.lms.admissions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.admissions.domain.AdmissionDecision;
import com.university.lms.admissions.domain.AdmissionsErrorCode;
import com.university.lms.admissions.access.ApplicationAccessGuard;
import com.university.lms.admissions.access.ApplicationAccessToken;
import com.university.lms.admissions.dto.ApplicationAccessResponse;
import com.university.lms.admissions.dto.ResumeApplicationRequest;
import com.university.lms.notification.api.EmailSender;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdmissionsService {

    private static final Logger log = LoggerFactory.getLogger(AdmissionsService.class);

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
    private final ProgrammeApplicationFormService applicationFormService;

    private final EmailSender emailSender;
    private final ApplicationAccessGuard accessGuard;

    /** How long a resume link stays usable. Bounds the damage of one that leaks. */
    private final Duration accessTokenTtl;

    private final String portalBaseUrl;

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
            ObjectMapper objectMapper,
            ProgrammeApplicationFormService applicationFormService,
            EmailSender emailSender,
            ApplicationAccessGuard accessGuard,
            @Value("${lms.admissions.access-token-ttl:P30D}") Duration accessTokenTtl,
            @Value("${lms.admissions.portal-base-url:http://localhost:5173}") String portalBaseUrl) {
        this.applicationRepository = applicationRepository;
        this.emailSender = emailSender;
        this.accessGuard = accessGuard;
        this.accessTokenTtl = accessTokenTtl;
        this.portalBaseUrl = portalBaseUrl;
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
        this.applicationFormService = applicationFormService;
    }

    /**
     * The staff view of an application by id — distinct from
     * {@link #findByIdForApplicant(UUID, String)}, which is reachable without an account at all
     * and is scoped by capability token instead. This one previously had no guard of its own: it
     * relied on {@code SecurityConfig}'s catch-all {@code GET /api/v1/** -> authenticated()}, which
     * meant any signed-in caller — a student included — could read any applicant's admissions
     * record, including decision notes and deposit status, simply by guessing or enumerating an id.
     */
    /**
     * The staff view of an application by id — distinct from
     * {@link #findByIdForApplicant(UUID, String)}, which is reachable without an account at all
     * and is scoped by capability token instead. This one previously had no guard of its own: it
     * relied on {@code SecurityConfig}'s catch-all {@code GET /api/v1/** -> authenticated()}, which
     * meant any signed-in caller — a student included — could read any applicant's admissions
     * record, including decision notes and deposit status, simply by guessing or enumerating an id.
     */
    public ApplicationResponse findById(UUID id) {
        requireStaffReader();
        return toResponse(require(id));
    }

    public PageResponse<ApplicationResponse> queue(
            List<ApplicationStatus> statuses, Boolean mineOnly, String reference, Pageable pageable) {
        CurrentUser caller = requireStaffReader();
        UUID assignedTo = Boolean.TRUE.equals(mineOnly) ? caller.userId() : null;
        List<ApplicationStatus> effectiveStatuses =
                statuses == null || statuses.isEmpty() ? defaultQueueStatuses() : statuses;
        return PageResponse.from(
                applicationRepository.search(
                        effectiveStatuses, assignedTo, referenceLikePattern(reference), pageable),
                this::toResponse);
    }

    @Transactional
    public ApplicationAccessResponse createDraft(CreateApplicationRequest request) {
        validateProgrammeAndTerm(request.programmeId(), request.academicTermId());
        assertNoOpenApplication(request.applicantEmail(), request.programmeId(), request.academicTermId());
        Map<String, Object> validatedPayload = applicationFormService.validatePayload(request.programmeId(), request.payload());

        String payloadJson = serializePayload(validatedPayload);
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

        // Minted here and returned exactly once: only the hash is persisted, so it can never be
        // shown again. Losing it is recoverable through resume(), which issues a fresh one by email.
        String token = ApplicationAccessToken.mint();
        Instant expiresAt = Instant.now().plus(accessTokenTtl);
        saved.issueAccessToken(ApplicationAccessToken.hash(token), expiresAt);

        ApplicationResponse body =
                Boolean.TRUE.equals(request.submit()) ? submit(saved.getId()) : toResponse(saved);
        return new ApplicationAccessResponse(body, token, expiresAt);
    }

    /**
     * Issues a fresh link to the address the application belongs to.
     *
     * <p>Always reports success, whether or not the pair matched. Saying "no such application" would
     * turn this into a way to test whether a given person applied to a given programme, which is
     * itself disclosure — and because the link goes to the registered address, an attacker who
     * guesses a valid pair still receives nothing.
     *
     * <p>Rotating the token invalidates every link issued earlier, so resuming doubles as a
     * revocation for a link the applicant thinks may have leaked.
     */
    @Transactional
    public void resume(ResumeApplicationRequest request) {
        Optional<Application> match = applicationRepository.findByReferenceIgnoreCaseAndApplicantEmailIgnoreCase(
                request.reference().trim(), request.applicantEmail().trim());

        if (match.isEmpty()) {
            log.info("Resume requested for an unknown reference/email pair; responding as though sent");
            return;
        }
        Application application = match.get();
        if (application.getStatus().terminal()) {
            // Nothing left to resume: a decided application is not editable and its outcome is
            // communicated separately. Silence here matches the unknown-pair case.
            log.info("Resume requested for terminal application {}; ignoring", application.getId());
            return;
        }

        String token = ApplicationAccessToken.mint();
        Instant expiresAt = Instant.now().plus(accessTokenTtl);
        application.issueAccessToken(ApplicationAccessToken.hash(token), expiresAt);
        applicationRepository.save(application);

        emailSender.send(new EmailSender.EmailMessage(
                application.getApplicantEmail(),
                "Continue your application " + application.getReference(),
                "Use the link below to continue your application, check its status, or upload documents."
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + portalBaseUrl + "/apply?application=" + application.getId() + "&token=" + token
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "Reference: " + application.getReference()
                        + System.lineSeparator()
                        + "This link expires on " + expiresAt + "."
                        + System.lineSeparator()
                        + "If you did not request it, you can ignore this message."));

        auditTrail.record(
                null,
                AuditTrail.Action.APPLICATION_ACCESS_REISSUED,
                AuditTrail.EntityType.APPLICATION,
                application.getId(),
                "Resume link reissued to the registered address");
    }

    /**
     * Applicant-facing read: requires the capability token, unless the caller is staff.
     *
     * <p>Separate from {@link #findById(UUID)}, which the admissions queue uses. Keeping them apart
     * means the staff path cannot be reached by an anonymous caller through a shared method, and the
     * guarded path cannot be bypassed by a future caller who forgets the check.
     */
    @Transactional
    public ApplicationResponse findByIdForApplicant(UUID id, String accessToken) {
        Application application = requireAccessible(id, accessToken);
        application.recordAccess(Instant.now());
        return toResponse(application);
    }

    /**
     * Loads an application and refuses unless the caller is staff or holds its token.
     *
     * <p>A caller who is refused gets the same answer whether the application exists or not, so this
     * cannot be used to discover which ids are real.
     */
    /**
     * Retires the capability once the application reaches a terminal state.
     *
     * <p>A denied or matriculated application cannot be edited and its outcome is communicated by
     * other means, so the token has nothing left to grant. Leaving it live would keep a credential
     * in circulation for no benefit — and credentials that outlive their purpose are the ones that
     * turn up later in a log or an inbox.
     */
    private void retireAccessTokenIfTerminal(Application application) {
        if (application.getStatus().terminal()) {
            application.revokeAccessToken();
        }
    }

    private Application requireAccessible(UUID id, String accessToken) {
        Application application = applicationRepository
                .findById(id)
                .orElseThrow(() -> new ForbiddenException(
                        AdmissionsErrorCode.APPLICATION_ACCESS_DENIED,
                        "You do not have access to this application. "
                                + "Use the link emailed to you, or start a new application."));
        accessGuard.requireAccess(application, accessToken);
        return application;
    }

    /** Guarded entry point; the applicant must hold the token, or the caller must be staff. */
    @Transactional
    public ApplicationResponse update(UUID id, String accessToken, UpdateApplicationRequest request) {
        requireAccessible(id, accessToken);
        return update(id, request);
    }

    @Transactional
    public ApplicationResponse submit(UUID id, String accessToken) {
        requireAccessible(id, accessToken);
        return submit(id);
    }

    @Transactional
    public ApplicationResponse attachDocument(UUID id, String accessToken, AttachApplicationDocumentRequest body) {
        requireAccessible(id, accessToken);
        return attachDocument(id, body);
    }

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
                request.payload() == null
                        ? null
                        : serializePayload(applicationFormService.validatePayload(
                                application.getProgrammeId(), request.payload())));
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
        applicationFormService.validatePayload(application.getProgrammeId(), parsePayload(application.getPayload()));
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
        retireAccessTokenIfTerminal(application);
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
                        user.id(), studentNumber, application.getProgrammeId(), admissionDate, null, null));
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

    /** Built in Java so a null filter never reaches SQL CONCAT (PostgreSQL types that as bytea). */
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

    private static String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[] {trimmed, "Applicant"};
        }
        return new String[] {trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }
}
