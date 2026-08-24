package com.university.lms.enrollment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.api.RecordAccessLog;
import com.university.lms.common.telemetry.UniFlowMetrics;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.course.api.Timetable;
import com.university.lms.curriculum.api.CurriculumCatalog;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.domain.EnrollmentCheckoutIdempotency;
import com.university.lms.enrollment.domain.EnrollmentErrorCode;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import com.university.lms.enrollment.dto.CheckoutEnrollmentsRequest;
import com.university.lms.enrollment.dto.CheckoutEnrollmentsResponse;
import com.university.lms.enrollment.dto.CreateEnrollmentRequest;
import com.university.lms.enrollment.dto.EnrollmentOverrideRequest;
import com.university.lms.enrollment.dto.EnrollmentResponse;
import com.university.lms.enrollment.repository.EnrollmentCheckoutIdempotencyRepository;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.financialaid.api.RegistrationHolds;
import com.university.lms.finance.api.StudentBilling;
import com.university.lms.student.api.StudentDirectory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration of students into course sections.
 *
 * <p>This is the module where the system's concurrency strategy is actually exercised, because
 * every student in the university tries to use it in the same fifteen minutes. Two distinct races
 * have to be survived, and neither is handled by an application-level {@code if}:
 *
 * <ol>
 *   <li><b>Double enrolment.</b> Two requests for the same (student, section) pass the existence
 *       check simultaneously. The unique index is what rejects the loser; the check merely gives
 *       the common case a good error message.
 *   <li><b>Over-filling a section.</b> Two requests both observe a free seat. Resolved by asking
 *       the course module to take the seat with a single guarded UPDATE — see
 *       {@code CourseSectionRepository.reserveSeat}. When that UPDATE says no, the student is
 *       waitlisted rather than refused.
 * </ol>
 *
 * <p>Both the seat reservation and the enrolment row are written in one transaction, so a failure
 * at any point returns the seat automatically rather than leaking capacity.
 */
@Service
@Transactional(readOnly = true)
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private static final List<EnrollmentStatus> ON_BOOKS =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.ENROLLED, EnrollmentStatus.WAITLISTED);

    private static final Duration WAITLIST_OFFER_WINDOW = Duration.ofHours(24);

    private final EnrollmentRepository repository;
    private final EnrollmentCheckoutIdempotencyRepository checkoutIdempotencyRepository;
    private final ObjectMapper objectMapper;
    private final StudentDirectory studentDirectory;
    private final CourseCatalog courseCatalog;
    private final CurriculumCatalog curriculumCatalog;
    private final AcademicStructure academicStructure;
    private final StudentBilling studentBilling;
    private final RegistrationHolds registrationHolds;
    private final CurrentUserProvider currentUserProvider;
    private final AuditTrail auditTrail;
    private final RecordAccessLog recordAccessLog;
    private final UniFlowMetrics metrics;

    public EnrollmentService(
            EnrollmentRepository repository,
            EnrollmentCheckoutIdempotencyRepository checkoutIdempotencyRepository,
            ObjectMapper objectMapper,
            StudentDirectory studentDirectory,
            CourseCatalog courseCatalog,
            CurriculumCatalog curriculumCatalog,
            AcademicStructure academicStructure,
            StudentBilling studentBilling,
            RegistrationHolds registrationHolds,
            CurrentUserProvider currentUserProvider,
            AuditTrail auditTrail,
            RecordAccessLog recordAccessLog,
            UniFlowMetrics metrics) {
        this.repository = repository;
        this.checkoutIdempotencyRepository = checkoutIdempotencyRepository;
        this.objectMapper = objectMapper;
        this.studentDirectory = studentDirectory;
        this.courseCatalog = courseCatalog;
        this.curriculumCatalog = curriculumCatalog;
        this.academicStructure = academicStructure;
        this.studentBilling = studentBilling;
        this.registrationHolds = registrationHolds;
        this.currentUserProvider = currentUserProvider;
        this.auditTrail = auditTrail;
        this.recordAccessLog = recordAccessLog;
        this.metrics = metrics;
    }

    /**
     * Enrols a student into a section.
     *
     * <p>Order matters: every cheap validation runs before the seat is taken, so a request that was
     * never going to succeed does not briefly consume capacity that a valid concurrent request
     * could have used. A full occurrence waitlists rather than returning 409. Minimum credit load
     * is not applied here — a single add of a 3-credit course must still work; the floor is
     * enforced at {@link #checkout}.
     */
    @Transactional
    public EnrollmentResponse enrol(CreateEnrollmentRequest request) {
        UUID studentId = request.studentId();
        UUID sectionId = request.courseSectionId();
        requireOwnStudentRecordOrStaff(studentId);
        StudentDirectory.StudentSummary student = requireEligibleStudent(studentId);
        CourseCatalog.SectionSummary section = requireOpenSection(sectionId);
        requireRegistrationOpen(section);
        requireNoRegistrationHolds(studentId, section.academicTermId());
        requireNotAlreadyOnBooks(studentId, sectionId);
        requireCourseRequirements(studentId, section.courseId());
        requireOnProgramme(student, section);
        requireNoTimetableClash(studentId, section);
        requireComponentOrder(studentId, List.of(section));
        requireCreditLoad(student, section.academicTermId(), creditsOf(section), false);
        return toResponse(persistEnrolment(student, section, null));
    }

    @Transactional
    public CheckoutEnrollmentsResponse checkout(CheckoutEnrollmentsRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<EnrollmentCheckoutIdempotency> existing =
                    checkoutIdempotencyRepository.findByStudentIdAndIdempotencyKey(request.studentId(), idempotencyKey);
            if (existing.isPresent()) {
                try {
                    return objectMapper.readValue(existing.get().getResponseJson(), CheckoutEnrollmentsResponse.class);
                } catch (JsonProcessingException ex) {
                    throw new BusinessException(
                            EnrollmentErrorCode.INVALID_ENROLLMENT_STATE, "Stored checkout response is invalid");
                }
            }
        }
        CheckoutEnrollmentsResponse response = checkoutInternal(request);
        if (idempotencyKey != null && !idempotencyKey.isBlank() && response.checkoutBatchId() != null) {
            try {
                checkoutIdempotencyRepository.save(new EnrollmentCheckoutIdempotency(
                        request.studentId(),
                        idempotencyKey,
                        response.checkoutBatchId(),
                        objectMapper.writeValueAsString(response)));
            } catch (JsonProcessingException ex) {
                throw new BusinessException(
                        EnrollmentErrorCode.INVALID_ENROLLMENT_STATE, "Could not persist checkout idempotency");
            }
        }
        return response;
    }

    /**
     * Confirms a registration cart. Validates the combined load against min and max, then enrols
     * or waitlists each occurrence. One failure rolls the whole cart back.
     */
    @Transactional
    public CheckoutEnrollmentsResponse checkout(CheckoutEnrollmentsRequest request) {
        return checkout(request, null);
    }

    private CheckoutEnrollmentsResponse checkoutInternal(CheckoutEnrollmentsRequest request) {
        requireOwnStudentRecordOrStaff(request.studentId());
        StudentDirectory.StudentSummary student = requireEligibleStudent(request.studentId());
        List<CourseCatalog.SectionSummary> sections = resolveCheckoutSections(request.courseSectionIds());
        List<EnrollmentResponse> created = new ArrayList<>();
        List<CourseCatalog.SectionSummary> incoming = new ArrayList<>();
        for (CourseCatalog.SectionSummary section : sections) {
            Enrollment already = onBooks(student.id(), section.id());
            if (already != null) {
                created.add(toResponse(already));
                continue;
            }
            requireRegistrationOpen(section);
            requireNoRegistrationHolds(student.id(), section.academicTermId());
            requireCourseRequirements(student.id(), section.courseId());
            requireOnProgramme(student, section);
            incoming.add(section);
        }
        UUID batchId = null;
        if (!incoming.isEmpty()) {
            requireCheckoutTimetable(student.id(), incoming);
            requireCheckoutCreditLoad(student, incoming);
            batchId = UUID.randomUUID();
            for (CourseCatalog.SectionSummary section : incoming) {
                created.add(toResponse(persistEnrolment(student, section, batchId)));
            }
        }
        Instant expiresAt = correctionDeadline(batchId == null ? null : Instant.now());
        return new CheckoutEnrollmentsResponse(batchId, expiresAt, created);
    }

    /**
     * Reverses every still-open row from the caller's own checkout, if the term window is open and
     * the university correction hours have not elapsed.
     */
    @Transactional
    public CheckoutEnrollmentsResponse undoOwnCheckout(UUID checkoutBatchId) {
        UUID studentId = ownStudentId(currentUserProvider.require());
        List<Enrollment> rows = repository.findByStudentIdAndCheckoutBatchId(studentId, checkoutBatchId).stream()
                .filter(row -> row.getStatus().occupiesTimetable())
                .toList();
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException(
                    EnrollmentErrorCode.ENROLLMENT_NOT_FOUND, "No open checkout exists with that id");
        }
        Instant now = Instant.now();
        Instant started = rows.stream().map(Enrollment::getEnrolledAt).min(Instant::compareTo).orElse(now);
        Instant deadline = correctionDeadline(started);
        if (deadline == null || now.isAfter(deadline)) {
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_CORRECTION_CLOSED,
                    "The correction window for this registration has closed.");
        }
        for (Enrollment row : rows) {
            CourseCatalog.SectionSummary section =
                    courseCatalog.findSection(row.getCourseSectionId()).orElse(null);
            if (section == null || !academicStructure.canDropWithoutPenalty(section.academicTermId(), now)) {
                throw new BusinessException(
                        EnrollmentErrorCode.ENROLLMENT_REGISTRATION_CLOSED,
                        "Registration and add/drop are closed, so this checkout can no longer be undone.");
            }
        }
        List<EnrollmentResponse> dropped = new ArrayList<>();
        for (Enrollment row : rows) {
            dropped.add(endEnrolment(row.getId(), EnrollmentStatus.DROPPED));
        }
        metrics.enrolment("checkout_undone");
        log.info("Student {} undid checkout {}", studentId, checkoutBatchId);
        return new CheckoutEnrollmentsResponse(checkoutBatchId, null, dropped);
    }

    /** Drops an enrolment and returns its seat to the section. */
    @Transactional
    public EnrollmentResponse drop(UUID enrollmentId) {
        requireOwnEnrolmentOrStaff(enrollmentId);
        return endEnrolment(enrollmentId, EnrollmentStatus.DROPPED);
    }

    /** Withdraws after the drop window; the record is retained on the transcript. */
    @Transactional
    public EnrollmentResponse withdraw(UUID enrollmentId) {
        requireOwnEnrolmentOrStaff(enrollmentId);
        return endEnrolment(enrollmentId, EnrollmentStatus.WITHDRAWN);
    }

    /**
     * Marks the enrolment completed. Registry may do this for any section; a lecturer only for a
     * section they teach. Role on the URL is not enough — that would let any lecturer complete any
     * student in the university.
     */
    @Transactional
    public EnrollmentResponse complete(UUID enrollmentId) {
        Enrollment enrolment = require(enrollmentId);
        requireTeacherOrRegistry(enrolment.getCourseSectionId());
        transition(enrolment, EnrollmentStatus.COMPLETED);
        // Checked after the transition, not before: transitionTo() already carries the correct
        // same-state-is-a-no-op and terminal-state rules, and re-deriving those here would drift.
        // Throwing here still rolls back the whole transaction, so an unpublished completion is
        // never persisted — this only changes which check reports first when both would fail.
        if (!curriculumCatalog.hasPublishedResult(enrolment.getStudentId(), enrolment.getCourseSectionId())) {
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_NO_PUBLISHED_RESULT,
                    "Cannot complete an enrolment with no published overall grade for this section");
        }
        return toResponse(enrolment);
    }

    /** Registrar override to enrol a student, bypassing window and hold checks. */
    @Transactional
    public EnrollmentResponse overrideEnrol(EnrollmentOverrideRequest request) {
        requireRegistrar();
        StudentDirectory.StudentSummary student = requireEligibleStudent(request.studentId());
        CourseCatalog.SectionSummary section = requireOpenSection(request.courseSectionId());
        requireNotAlreadyOnBooks(student.id(), section.id());
        requireCourseRequirements(student.id(), section.courseId());
        requireOnProgramme(student, section);
        requireNoTimetableClash(student.id(), section);
        Enrollment saved = persistEnrolment(student, section, null);
        recordAudit(
                AuditTrail.Action.ENROLMENT_CREATED,
                saved.getId(),
                "override · "
                        + request.reasonCode()
                        + " · "
                        + student.studentNumber()
                        + " · "
                        + section.courseCode()
                        + " "
                        + section.sectionCode());
        return toResponse(saved);
    }

    /** Approves a PENDING enrolment for a restricted section. */
    @Transactional
    public EnrollmentResponse approvePending(UUID enrollmentId) {
        Enrollment enrolment = require(enrollmentId);
        requireTeacherOrRegistry(enrolment.getCourseSectionId());
        if (enrolment.getStatus() != EnrollmentStatus.PENDING) {
            throw new BusinessException(
                    EnrollmentErrorCode.INVALID_ENROLLMENT_STATE, "Only pending enrolments may be approved");
        }
        transition(enrolment, EnrollmentStatus.ENROLLED);
        courseCatalog.findSection(enrolment.getCourseSectionId()).ifPresent(section -> billForEnrolment(enrolment, section));
        recordAudit(
                AuditTrail.Action.ENROLMENT_CREATED,
                enrolment.getId(),
                "approved · section " + enrolment.getCourseSectionId());
        return toResponse(enrolment);
    }

    /** Accepts a waitlist promotion before the offer expires. */
    @Transactional
    public EnrollmentResponse acceptWaitlistOffer(UUID enrollmentId) {
        Enrollment enrolment = require(enrollmentId);
        requireOwnStudentRecordOrStaff(enrolment.getStudentId());
        if (enrolment.getStatus() != EnrollmentStatus.ENROLLED || enrolment.getWaitlistOfferExpiresAt() == null) {
            throw new BusinessException(
                    EnrollmentErrorCode.INVALID_ENROLLMENT_STATE, "No waitlist offer is pending acceptance");
        }
        if (Instant.now().isAfter(enrolment.getWaitlistOfferExpiresAt())) {
            throw new BusinessException(EnrollmentErrorCode.INVALID_ENROLLMENT_STATE, "Waitlist offer has expired");
        }
        enrolment.clearWaitlistOffer();
        return toResponse(enrolment);
    }

    /** Declines a waitlist promotion; seat is released and the next student is promoted. */
    @Transactional
    public EnrollmentResponse declineWaitlistOffer(UUID enrollmentId) {
        Enrollment enrolment = require(enrollmentId);
        requireOwnStudentRecordOrStaff(enrolment.getStudentId());
        if (enrolment.getStatus() != EnrollmentStatus.ENROLLED || enrolment.getWaitlistOfferExpiresAt() == null) {
            throw new BusinessException(
                    EnrollmentErrorCode.INVALID_ENROLLMENT_STATE, "No waitlist offer is pending acceptance");
        }
        return endEnrolment(enrollmentId, EnrollmentStatus.DROPPED);
    }

    public EnrollmentResponse findById(UUID enrollmentId) {
        Enrollment enrolment = require(enrollmentId);
        requireOwnStudentRecordOrStaff(enrolment.getStudentId());
        return toResponse(enrolment);
    }

    /**
     * A student's listing is forced to their own record rather than refused, because an enrolment
     * list is exactly what a student portal needs. Staff may filter freely.
     */
    public PageResponse<EnrollmentResponse> search(
            UUID studentId, UUID courseSectionId, EnrollmentStatus status, Pageable pageable) {
        CurrentUser caller = currentUserProvider.require();
        UUID effectiveStudentId = studentId;
        if (!caller.isStaff()) {
            UUID own = ownStudentId(caller);
            if (studentId != null && !studentId.equals(own)) {
                throw new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
            }
            effectiveStudentId = own;
        } else if (studentId != null) {
            recordAccessLog.record(
                    caller.userId(),
                    caller.fullName(),
                    studentId,
                    RecordAccessLog.RecordType.ENROLLMENT,
                    RecordAccessLog.Action.VIEW,
                    "Enrolment history");
        }
        return PageResponse.from(
                repository.search(effectiveStudentId, courseSectionId, status, pageable), this::toResponse);
    }

    private void requireRegistrar() {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
    }

    private EnrollmentResponse toResponse(Enrollment enrolment) {
        Integer position = null;
        if (enrolment.getStatus() == EnrollmentStatus.WAITLISTED) {
            position = (int) repository.countWaitlistedAhead(enrolment.getCourseSectionId(), enrolment.getEnrolledAt())
                    + 1;
        }
        return EnrollmentResponse.from(enrolment, position);
    }

    // ---------------------------------------------------------------------
    // Ownership
    // ---------------------------------------------------------------------

    /**
     * Ownership here is by <em>student record</em>, not by user, so the check has to go through the
     * student module's published contract rather than comparing user ids directly.
     */
    private void requireOwnStudentRecordOrStaff(UUID studentId) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.isStaff()) {
            return;
        }
        if (!ownStudentId(caller).equals(studentId)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }

    private void requireOwnEnrolmentOrStaff(UUID enrollmentId) {
        requireOwnStudentRecordOrStaff(require(enrollmentId).getStudentId());
    }

    /**
     * Completing is an academic judgement on a <em>section</em>, not a student-owned action. The
     * course module answers who teaches it; this module only asks.
     */
    private void requireTeacherOrRegistry(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return;
        }
        if (caller.hasRole(SecurityRoles.LECTURER) && courseCatalog.teaches(caller.userId(), sectionId)) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
    }

    /** Late-add enrolment on behalf of staff after an approved petition. */
    @Transactional
    public EnrollmentResponse lateAdd(UUID studentId, UUID courseSectionId) {
        requireRegistrar();
        return overrideEnrol(new EnrollmentOverrideRequest(studentId, courseSectionId, "LATE_ADD", null));
    }

    /** A non-staff caller with no student record owns nothing, and is refused rather than matched. */
    private UUID ownStudentId(CurrentUser caller) {
        return studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private StudentDirectory.StudentSummary requireEligibleStudent(UUID studentId) {
        StudentDirectory.StudentSummary student = studentDirectory
                .findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EnrollmentErrorCode.ENROLLMENT_STUDENT_NOT_FOUND,
                        "No student exists with id " + studentId));
        if (!student.eligibleToEnrol()) {
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_STUDENT_NOT_ELIGIBLE,
                    "Student " + student.studentNumber() + " is not in good standing to register");
        }
        return student;
    }

    private CourseCatalog.SectionSummary requireOpenSection(UUID sectionId) {
        CourseCatalog.SectionSummary section = courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EnrollmentErrorCode.ENROLLMENT_SECTION_NOT_FOUND,
                        "No course section exists with id " + sectionId));
        if (!section.openForEnrolment()) {
            metrics.enrolment("closed");
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_SECTION_NOT_OPEN,
                    "Section " + section.courseCode() + "-" + section.sectionCode()
                            + " is not open for registration");
        }
        return section;
    }

    private void requireRegistrationOpen(CourseCatalog.SectionSummary section) {
        if (!academicStructure.canAddEnrolment(section.academicTermId(), Instant.now())) {
            metrics.enrolment("closed");
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_REGISTRATION_CLOSED,
                    "Registration is not currently open for this term");
        }
    }

    /**
     * Blocks registration when payment-plan, service, or SAP holds are active.
     *
     * <p>Hook point: call from {@link #enrol} and {@link #checkout} before seat reservation. SAP
     * holds are placed by {@code SapService.evaluateAfterGrades} after term grades publish — wire
     * that call from the grading module when overall grades are finalized (see
     * {@code docs/financial-aid-enrollment-hooks.md}).
     */
    private void requireNoRegistrationHolds(UUID studentId, UUID termId) {
        requireNoFinancialHold(studentId, termId);
        var holds = registrationHolds.activeRegistrationHolds(studentId);
        if (holds.isEmpty()) {
            return;
        }
        var first = holds.get(0);
        metrics.enrolment("hold");
        EnrollmentErrorCode code = switch (first.type()) {
            case "SAP" -> EnrollmentErrorCode.ENROLLMENT_SAP_HOLD;
            case "FINANCIAL" -> EnrollmentErrorCode.ENROLLMENT_FINANCIAL_HOLD;
            default -> EnrollmentErrorCode.ENROLLMENT_REGISTRATION_HOLD;
        };
        throw new BusinessException(code, first.reason());
    }

    private void requireNoFinancialHold(UUID studentId, UUID termId) {
        var standing = studentBilling.standingOf(studentId, termId, java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        if (standing.hold()) {
            metrics.enrolment("hold");
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_FINANCIAL_HOLD,
                    standing.reason() == null
                            ? "A financial hold is on this account. Pay the required installment to continue."
                            : standing.reason());
        }
    }

    private void requireNotAlreadyOnBooks(UUID studentId, UUID sectionId) {
        if (onBooks(studentId, sectionId) == null) {
            return;
        }
        metrics.enrolment("duplicate");
        CourseCatalog.SectionSummary section = courseCatalog.findSection(sectionId).orElse(null);
        String course = section == null ? "this course" : section.courseCode();
        throw new ResourceAlreadyExistsException(
                EnrollmentErrorCode.ENROLLMENT_ALREADY_EXISTS, "You're already registered for " + course + ".");
    }

    private Enrollment onBooks(UUID studentId, UUID sectionId) {
        List<Enrollment> rows =
                repository.findByStudentIdAndCourseSectionIdAndStatusIn(studentId, sectionId, ON_BOOKS);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void requireOnProgramme(StudentDirectory.StudentSummary student, CourseCatalog.SectionSummary section) {
        if (curriculumCatalog.allowsEnrolment(student.programmeId(), section.courseId())) {
            return;
        }
        throw new BusinessException(
                EnrollmentErrorCode.ENROLLMENT_NOT_ON_PROGRAMME,
                section.courseCode() + " is not on this student's programme curriculum");
    }

    private List<CourseCatalog.SectionSummary> resolveCheckoutSections(List<UUID> sectionIds) {
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        for (UUID sectionId : sectionIds) {
            if (sectionId != null) {
                unique.add(sectionId);
            }
        }
        if (unique.isEmpty()) {
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_SECTION_NOT_FOUND, "Select at least one course section");
        }
        List<CourseCatalog.SectionSummary> sections = new ArrayList<>();
        for (UUID sectionId : unique) {
            sections.add(requireOpenSection(sectionId));
        }
        return sections;
    }

    /**
     * Completed means an enrolment in {@code COMPLETED}, not a published grade — this module must
     * not call the grading contract. Co-requisites may already be {@code ENROLLED} this term.
     */
    private void requireCourseRequirements(UUID studentId, UUID courseId) {
        List<Enrollment> history = repository.findByStudentIdAndStatusIn(
                studentId, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.COMPLETED));

        // One query for every section in the history and one for every course, rather than two
        // per row — the naive version issues two lookups per historical enrolment, on exactly the
        // path that spikes hardest during a registration rush.
        Set<UUID> sectionIds =
                history.stream().map(Enrollment::getCourseSectionId).collect(Collectors.toSet());
        Map<UUID, CourseCatalog.SectionSummary> sectionsById = courseCatalog.findSections(sectionIds).stream()
                .collect(Collectors.toMap(CourseCatalog.SectionSummary::id, s -> s));
        Set<UUID> courseIds = sectionsById.values().stream()
                .map(CourseCatalog.SectionSummary::courseId)
                .collect(Collectors.toSet());
        Map<UUID, CourseCatalog.CourseSummary> coursesById = courseCatalog.findCourses(courseIds).stream()
                .collect(Collectors.toMap(CourseCatalog.CourseSummary::id, c -> c));

        Set<UUID> completed = new HashSet<>();
        Set<UUID> inProgress = new HashSet<>();
        int highestCompletedLevel = 0;
        for (Enrollment row : history) {
            CourseCatalog.SectionSummary section = sectionsById.get(row.getCourseSectionId());
            if (section == null) {
                continue;
            }
            CourseCatalog.CourseSummary course = coursesById.get(section.courseId());
            if (course == null) {
                continue;
            }
            if (row.getStatus() == EnrollmentStatus.COMPLETED) {
                // A COMPLETED enrolment with a failing published grade satisfies no prerequisite —
                // "completed" records that the course was sat, not that it was passed.
                if (curriculumCatalog.hasPassed(studentId, course.id())) {
                    completed.add(course.id());
                    highestCompletedLevel = Math.max(highestCompletedLevel, course.level());
                }
            } else if (row.getStatus() == EnrollmentStatus.ENROLLED) {
                inProgress.add(course.id());
            }
        }

        // A transfer student may satisfy a prerequisite with credit from another institution and
        // never have an internal enrolment row for the course at all — the loop above cannot find
        // that from history alone.
        Set<UUID> transferCredited = curriculumCatalog.transferCreditedCourseIds(studentId);
        if (!transferCredited.isEmpty()) {
            for (CourseCatalog.CourseSummary course : courseCatalog.findCourses(transferCredited)) {
                completed.add(course.id());
                highestCompletedLevel = Math.max(highestCompletedLevel, course.level());
            }
        }

        List<String> unmet =
                courseCatalog.unmetRequirements(courseId, completed, inProgress, highestCompletedLevel);
        if (!unmet.isEmpty()) {
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_PREREQUISITE_NOT_MET, String.join("; ", unmet));
        }
    }

    private void requireNoTimetableClash(UUID studentId, CourseCatalog.SectionSummary incoming) {
        List<CourseCatalog.Meeting> meetings = courseCatalog.meetingsOf(incoming.id());
        if (Timetable.selfClash(meetings)) {
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_TIMETABLE_CLASH,
                    incoming.courseCode() + " " + incoming.sectionCode() + " has overlapping sessions");
        }
        for (Enrollment held : onBooksInTerm(studentId, incoming.academicTermId())) {
            if (held.getCourseSectionId().equals(incoming.id())) {
                continue;
            }
            List<CourseCatalog.Meeting> other = courseCatalog.meetingsOf(held.getCourseSectionId());
            if (Timetable.clashes(meetings, other)) {
                CourseCatalog.SectionSummary clash = courseCatalog.findSection(held.getCourseSectionId()).orElse(null);
                String label = clash == null
                        ? "another enrolled occurrence"
                        : clash.courseCode() + " " + clash.sectionCode();
                throw new BusinessException(
                        EnrollmentErrorCode.ENROLLMENT_TIMETABLE_CLASH,
                        incoming.courseCode() + " " + incoming.sectionCode() + " clashes with " + label);
            }
        }
    }

    private void requireCheckoutTimetable(UUID studentId, List<CourseCatalog.SectionSummary> incoming) {
        for (int i = 0; i < incoming.size(); i++) {
            requireNoTimetableClash(studentId, incoming.get(i));
            List<CourseCatalog.Meeting> left = courseCatalog.meetingsOf(incoming.get(i).id());
            for (int j = i + 1; j < incoming.size(); j++) {
                if (Timetable.clashes(left, courseCatalog.meetingsOf(incoming.get(j).id()))) {
                    throw new BusinessException(
                            EnrollmentErrorCode.ENROLLMENT_TIMETABLE_CLASH,
                            incoming.get(i).courseCode()
                                    + " "
                                    + incoming.get(i).sectionCode()
                                    + " clashes with "
                                    + incoming.get(j).courseCode()
                                    + " "
                                    + incoming.get(j).sectionCode());
                }
            }
        }
        Map<UUID, List<CourseCatalog.SectionSummary>> byCourse = new LinkedHashMap<>();
        for (CourseCatalog.SectionSummary section : incoming) {
            byCourse.computeIfAbsent(section.courseId(), key -> new ArrayList<>()).add(section);
        }
        for (List<CourseCatalog.SectionSummary> group : byCourse.values()) {
            requireComponentOrder(studentId, group);
        }
    }

    private void requireComponentOrder(UUID studentId, List<CourseCatalog.SectionSummary> adding) {
        if (adding.isEmpty()) {
            return;
        }
        UUID courseId = adding.get(0).courseId();
        UUID termId = adding.get(0).academicTermId();
        List<List<CourseCatalog.Meeting>> occurrences = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (CourseCatalog.SectionSummary section : adding) {
            occurrences.add(courseCatalog.meetingsOf(section.id()));
            seen.add(section.id());
        }
        for (Enrollment held : onBooksInTerm(studentId, termId)) {
            if (seen.contains(held.getCourseSectionId())) {
                continue;
            }
            courseCatalog.findSection(held.getCourseSectionId()).ifPresent(section -> {
                if (section.courseId().equals(courseId)) {
                    occurrences.add(courseCatalog.meetingsOf(section.id()));
                }
            });
        }
        Timetable.componentOrderIssue(occurrences).ifPresent(message -> {
            throw new BusinessException(EnrollmentErrorCode.ENROLLMENT_COMPONENT_ORDER, message);
        });
    }

    private void requireCreditLoad(
            StudentDirectory.StudentSummary student, UUID termId, int adding, boolean enforceMinimum) {
        int current = creditsInTerm(student.id(), termId);
        var load = academicStructure.creditLoadFor(student.programmeId());
        int total = current + adding;
        if (total > load.maxSemesterCredits()) {
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_CREDIT_LOAD_EXCEEDED,
                    "This enrolment would take the load to "
                            + total
                            + " credits; the maximum for this programme is "
                            + load.maxSemesterCredits());
        }
        if (enforceMinimum && total < load.minSemesterCredits()) {
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_CREDIT_LOAD_BELOW_MINIMUM,
                    "Registration requires at least "
                            + load.minSemesterCredits()
                            + " credits; this selection is "
                            + total);
        }
    }

    private void requireCheckoutCreditLoad(
            StudentDirectory.StudentSummary student, List<CourseCatalog.SectionSummary> sections) {
        Map<UUID, Integer> addingByTerm = new LinkedHashMap<>();
        for (CourseCatalog.SectionSummary section : sections) {
            addingByTerm.merge(section.academicTermId(), creditsOf(section), Integer::sum);
        }
        for (Map.Entry<UUID, Integer> entry : addingByTerm.entrySet()) {
            requireCreditLoad(student, entry.getKey(), entry.getValue(), true);
        }
    }

    private int creditsOf(CourseCatalog.SectionSummary section) {
        return courseCatalog.findCourse(section.courseId()).map(CourseCatalog.CourseSummary::credits).orElse(0);
    }

    private int creditsInTerm(UUID studentId, UUID termId) {
        return repository.findByStudentIdAndStatusIn(studentId, List.of(EnrollmentStatus.ENROLLED)).stream()
                .mapToInt(row -> courseCatalog
                        .findSection(row.getCourseSectionId())
                        .filter(section -> section.academicTermId().equals(termId))
                        .flatMap(section -> courseCatalog.findCourse(section.courseId()))
                        .map(CourseCatalog.CourseSummary::credits)
                        .orElse(0))
                .sum();
    }

    private List<Enrollment> onBooksInTerm(UUID studentId, UUID termId) {
        return repository.findByStudentIdAndStatusIn(studentId, ON_BOOKS).stream()
                .filter(row -> courseCatalog
                        .findSection(row.getCourseSectionId())
                        .map(section -> section.academicTermId().equals(termId))
                        .orElse(false))
                .toList();
    }

    private Enrollment persistEnrolment(
            StudentDirectory.StudentSummary student, CourseCatalog.SectionSummary section, UUID checkoutBatchId) {
        int attemptNumber = nextAttemptNumber(student.id(), section.courseId());
        if (courseCatalog.tryReserveSeat(section.id())) {
            EnrollmentStatus initialStatus =
                    section.requiresApproval() ? EnrollmentStatus.PENDING : EnrollmentStatus.ENROLLED;
            Enrollment saved;
            try {
                saved = repository.saveAndFlush(new Enrollment(
                        student.id(), section.id(), initialStatus, checkoutBatchId, attemptNumber));
            } catch (DataIntegrityViolationException ex) {
                throw asDuplicateOrRethrow(student.id(), section, ex);
            }
            if (initialStatus == EnrollmentStatus.ENROLLED) {
                billForEnrolment(saved, section);
            }
            recordAudit(
                    AuditTrail.Action.ENROLMENT_CREATED,
                    saved.getId(),
                    student.studentNumber() + " · " + section.courseCode() + " " + section.sectionCode());
            metrics.enrolment(initialStatus == EnrollmentStatus.PENDING ? "pending" : "created");
            log.info("Enrolled student {} into section {} as {}", student.id(), section.id(), initialStatus);
            return saved;
        }
        try {
            Enrollment saved = repository.saveAndFlush(new Enrollment(
                    student.id(), section.id(), EnrollmentStatus.WAITLISTED, checkoutBatchId, attemptNumber));
            recordAudit(
                    AuditTrail.Action.ENROLMENT_CREATED,
                    saved.getId(),
                    "waitlisted · " + student.studentNumber() + " · " + section.courseCode() + " "
                            + section.sectionCode());
            metrics.enrolment("waitlisted");
            log.info("Waitlisted student {} for section {}", student.id(), section.id());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw asDuplicateOrRethrow(student.id(), section, ex);
        }
    }

    /**
     * Next sit number for this student on the underlying course. Prior COMPLETED / WITHDRAWN /
     * ENROLLED / PENDING rows count; DROPPED and WAITLISTED alone do not.
     */
    private int nextAttemptNumber(UUID studentId, UUID courseId) {
        Set<EnrollmentStatus> sitStatuses = Set.of(
                EnrollmentStatus.COMPLETED,
                EnrollmentStatus.WITHDRAWN,
                EnrollmentStatus.ENROLLED,
                EnrollmentStatus.PENDING);
        int max = 0;
        for (Enrollment row : repository.findByStudentIdAndStatusIn(studentId, sitStatuses)) {
            Optional<CourseCatalog.SectionSummary> prior = courseCatalog.findSection(row.getCourseSectionId());
            if (prior.isPresent() && prior.get().courseId().equals(courseId)) {
                max = Math.max(max, row.getAttemptNumber());
            }
        }
        return max + 1;
    }

    private Instant correctionDeadline(Instant started) {
        if (started == null) {
            return null;
        }
        int hours = academicStructure.checkoutCorrectionHours();
        if (hours <= 0) {
            return null;
        }
        return started.plus(Duration.ofHours(hours));
    }

    private RuntimeException asDuplicateOrRethrow(
            UUID studentId, CourseCatalog.SectionSummary section, DataIntegrityViolationException ex) {
        if (!isActiveEnrolmentCollision(ex)) {
            return ex;
        }
        log.debug("Concurrent duplicate enrolment rejected for student {} section {}", studentId, section.id());
        metrics.enrolment("duplicate");
        return new ResourceAlreadyExistsException(
                EnrollmentErrorCode.ENROLLMENT_ALREADY_EXISTS,
                "You're already registered for " + section.courseCode() + ".",
                ex);
    }

    private static boolean isActiveEnrolmentCollision(DataIntegrityViolationException ex) {
        String detail = String.valueOf(ex.getMostSpecificCause().getMessage());
        String outer = String.valueOf(ex.getMessage());
        return detail.contains("uk_enrollments_student_section_active")
                || outer.contains("uk_enrollments_student_section_active");
    }

    /**
     * Releases the seat only if this enrolment was actually holding one, so that dropping an
     * already-dropped registration cannot hand a phantom seat back to the section.
     */
    private EnrollmentResponse endEnrolment(UUID enrollmentId, EnrollmentStatus target) {
        Enrollment enrolment = require(enrollmentId);
        boolean wasHoldingSeat = enrolment.occupiesSeat();
        if (wasHoldingSeat && target == EnrollmentStatus.DROPPED) {
            requireDropWindow(enrolment);
        }
        if (wasHoldingSeat && target == EnrollmentStatus.WITHDRAWN) {
            requireWithdrawWindow(enrolment);
        }

        CourseCatalog.SectionSummary section =
                wasHoldingSeat ? courseCatalog.findSection(enrolment.getCourseSectionId()).orElse(null) : null;

        transition(enrolment, target);

        if (wasHoldingSeat) {
            courseCatalog.releaseSeat(enrolment.getCourseSectionId());
            if (target == EnrollmentStatus.DROPPED && section != null) {
                boolean remaining = hasOtherEnrolmentInTerm(enrolment.getStudentId(), section.academicTermId());
                studentBilling.creditForDrop(
                        enrolment.getStudentId(),
                        enrolment.getId(),
                        section.academicTermId(),
                        section.courseCode(),
                        !remaining);
            }
            if (target == EnrollmentStatus.WITHDRAWN && section != null) {
                // E4: a withdrawal used to earn no credit at all, regardless of how soon after
                // add/drop closed it happened. studentBilling now tapers the refund by how long
                // since the no-penalty window ended.
                boolean remaining = hasOtherEnrolmentInTerm(enrolment.getStudentId(), section.academicTermId());
                studentBilling.creditForWithdrawal(
                        enrolment.getStudentId(),
                        enrolment.getId(),
                        section.academicTermId(),
                        section.courseCode(),
                        !remaining);
            }
            promoteWaitlist(enrolment.getCourseSectionId());
        }
        String action = target == EnrollmentStatus.DROPPED
                ? AuditTrail.Action.ENROLMENT_DROPPED
                : target == EnrollmentStatus.WITHDRAWN
                        ? AuditTrail.Action.ENROLMENT_WITHDRAWN
                        : null;
        if (action != null) {
            String targetLabel = courseCatalog
                    .findSection(enrolment.getCourseSectionId())
                    .map(s -> s.courseCode() + " " + s.sectionCode())
                    .orElse(enrolment.getCourseSectionId().toString());
            recordAudit(action, enrolment.getId(), targetLabel);
        }
        log.info("Enrolment {} moved to {}", enrollmentId, target);
        return toResponse(enrolment);
    }

    private void promoteWaitlist(UUID sectionId) {
        List<Enrollment> waiting =
                repository.findByCourseSectionIdAndStatusOrderByEnrolledAtAsc(sectionId, EnrollmentStatus.WAITLISTED);
        if (waiting.isEmpty()) {
            return;
        }
        Enrollment next = waiting.get(0);
        if (!courseCatalog.tryReserveSeat(sectionId)) {
            return;
        }
        transition(next, EnrollmentStatus.ENROLLED);
        next.offerWaitlistUntil(Instant.now().plus(WAITLIST_OFFER_WINDOW));
        courseCatalog.findSection(sectionId).ifPresent(section -> {
            billForEnrolment(next, section);
            recordAudit(
                    AuditTrail.Action.ENROLMENT_CREATED,
                    next.getId(),
                    "promoted · " + section.courseCode() + " " + section.sectionCode());
        });
        metrics.enrolment("promoted");
        log.info("Promoted waitlisted enrolment {} into section {}", next.getId(), sectionId);
    }

    private void requireDropWindow(Enrollment enrolment) {
        var section = courseCatalog.findSection(enrolment.getCourseSectionId());
        if (section.isEmpty()) {
            return;
        }
        if (!academicStructure.canDropWithoutPenalty(section.get().academicTermId(), Instant.now())) {
            throw new BusinessException(
                    EnrollmentErrorCode.ENROLLMENT_ADD_DROP_CLOSED,
                    "The add/drop period has ended. Withdraw from this course instead.");
        }
    }

    private void requireWithdrawWindow(Enrollment enrolment) {
        var section = courseCatalog.findSection(enrolment.getCourseSectionId());
        if (section.isEmpty()) {
            return;
        }
        if (academicStructure.canDropWithoutPenalty(section.get().academicTermId(), Instant.now())) {
            throw new BusinessException(
                    EnrollmentErrorCode.INVALID_ENROLLMENT_STATE,
                    "Drop this course instead — the add/drop period is still open");
        }
    }

    private void billForEnrolment(Enrollment saved, CourseCatalog.SectionSummary section) {
        int credits = courseCatalog.findCourse(section.courseId()).map(CourseCatalog.CourseSummary::credits).orElse(0);
        java.time.LocalDate dueOn = academicStructure
                .findCalendar(section.academicTermId(), Instant.now())
                .map(AcademicStructure.TermCalendar::tuitionDueOn)
                .orElse(null);
        studentBilling.chargeForEnrolment(
                saved.getStudentId(),
                saved.getId(),
                section.academicTermId(),
                section.courseCode(),
                section.courseId(),
                credits,
                dueOn);
    }

    private boolean hasOtherEnrolmentInTerm(UUID studentId, UUID termId) {
        return repository
                .findByStudentIdAndStatusIn(studentId, List.of(EnrollmentStatus.ENROLLED))
                .stream()
                .anyMatch(row -> courseCatalog
                        .findSection(row.getCourseSectionId())
                        .map(section -> section.academicTermId().equals(termId))
                        .orElse(false));
    }

    /** Translates the domain's transition guard into the API's error contract. */
    private void transition(Enrollment enrolment, EnrollmentStatus target) {
        try {
            enrolment.transitionTo(target);
        } catch (IllegalStateException ex) {
            throw new BusinessException(EnrollmentErrorCode.INVALID_ENROLLMENT_STATE, ex.getMessage());
        }
    }

    private Enrollment require(UUID enrollmentId) {
        return repository
                .findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EnrollmentErrorCode.ENROLLMENT_NOT_FOUND, "No enrolment exists with id " + enrollmentId));
    }

    private void recordAudit(String action, UUID entityId, String details) {
        CurrentUser actor = currentUserProvider.find().orElse(null);
        auditTrail.record(
                actor == null ? null : actor.userId(),
                actor == null ? null : actorLabel(actor),
                action,
                AuditTrail.EntityType.ENROLLMENT,
                entityId,
                details);
    }

    private static String actorLabel(CurrentUser actor) {
        if (actor.fullName() != null && !actor.fullName().isBlank()) {
            return actor.fullName();
        }
        return actor.username();
    }
}
