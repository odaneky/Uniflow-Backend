package com.university.lms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.api.RecordAccessLog;
import com.university.lms.common.telemetry.UniFlowMetrics;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.curriculum.api.CurriculumCatalog;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.domain.EnrollmentErrorCode;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import com.university.lms.enrollment.dto.CheckoutEnrollmentsRequest;
import com.university.lms.enrollment.dto.CreateEnrollmentRequest;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.enrollment.dto.EnrollmentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.enrollment.repository.EnrollmentCheckoutIdempotencyRepository;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.enrollment.service.EnrollmentService;
import com.university.lms.financialaid.api.RegistrationHolds;
import com.university.lms.finance.api.StudentBilling;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.ResidencyClassification;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

/**
 * Behaviour of the enrolment use case, with its collaborating modules stubbed at their published
 * API boundary — which is also a check that this service depends on nothing else.
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID SECTION_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID TERM_ID = UUID.randomUUID();

    @Mock
    private EnrollmentRepository repository;

    @Mock
    private EnrollmentCheckoutIdempotencyRepository checkoutIdempotencyRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private CourseCatalog courseCatalog;

    @Mock
    private CurriculumCatalog curriculumCatalog;

    @Mock
    private AcademicStructure academicStructure;

    @Mock
    private StudentBilling studentBilling;

    @Mock
    private RegistrationHolds registrationHolds;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private AuditTrail auditTrail;

    @Mock
    private RecordAccessLog recordAccessLog;

    @Mock
    private UniFlowMetrics metrics;

    @Mock
    private StaffAppointments staffAppointments;

    @InjectMocks
    private EnrollmentService service;

    private CreateEnrollmentRequest request;

    /**
     * These tests are about enrolment rules, not about who may invoke them, so the caller is staff
     * throughout; ownership is covered end to end by {@code OwnerScopingIntegrationTest}, where a
     * real token resolves to a real student record.
     *
     * <p>{@code lenient} because two of these tests never reach the ownership check — looking up an
     * enrolment that does not exist, and a forbidden status transition, both fail earlier. That is
     * correct behaviour, not an untested path.
     */
    @BeforeEach
    void setUp() {
        request = new CreateEnrollmentRequest(STUDENT_ID, SECTION_ID);
        lenient().when(currentUserProvider.require()).thenReturn(STAFF_CALLER);
        lenient().when(currentUserProvider.find()).thenReturn(Optional.of(STAFF_CALLER));
        lenient().when(repository.findByStudentIdAndStatusIn(eq(STUDENT_ID), any())).thenReturn(List.of());
        lenient().when(repository.findByStudentIdAndCourseSectionIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        lenient().when(repository.findByCourseSectionIdAndStatusOrderByEnrolledAtAsc(any(), any())).thenReturn(List.of());
        lenient().when(courseCatalog.unmetRequirements(any(), any(), any(), anyInt())).thenReturn(List.of());
        lenient().when(courseCatalog.meetingsOf(any())).thenReturn(List.of());
        lenient().when(curriculumCatalog.allowsEnrolment(any(), any())).thenReturn(true);
        lenient().when(curriculumCatalog.hasPublishedResult(any(), any())).thenReturn(true);
        lenient().when(curriculumCatalog.hasPassed(any(), any())).thenReturn(true);
        lenient().when(curriculumCatalog.transferCreditedCourseIds(any())).thenReturn(Set.of());
        lenient()
                .when(courseCatalog.findCourse(COURSE_ID))
                .thenReturn(Optional.of(new CourseCatalog.CourseSummary(
                        COURSE_ID, "COMP3101", "Programming", 3, 2, true)));
        lenient().when(academicStructure.findCalendar(eq(TERM_ID), any(Instant.class))).thenReturn(Optional.empty());
        lenient()
                .when(studentBilling.standingOf(any(), any(), any()))
                .thenReturn(com.university.lms.finance.api.PaymentStanding.none());
        lenient().when(registrationHolds.activeRegistrationHolds(any())).thenReturn(List.of());
        lenient()
                .when(academicStructure.creditLoadFor(any()))
                .thenReturn(new AcademicStructure.CreditLoad(12, 18, false));
        lenient().when(academicStructure.checkoutCorrectionHours()).thenReturn(48);
        lenient().when(staffAppointments.activeAppointmentsOf(any())).thenReturn(List.of());
    }

    private static final CurrentUser STAFF_CALLER = new CurrentUser(
            UUID.randomUUID(),
            "subject-registrar",
            "registrar",
            "registrar@university.test",
            "Rita Registrar",
            java.util.Optional.empty(),
            java.util.Set.of(SecurityRoles.REGISTRAR),
            java.util.Set.of());

    private static final UUID USER_ID = UUID.randomUUID();
    private static final CurrentUser STUDENT_CALLER = new CurrentUser(
            USER_ID,
            "subject-student",
            "202012345",
            "student@university.test",
            "Demo Student",
            java.util.Optional.of("202012345"),
            java.util.Set.of(SecurityRoles.STUDENT),
            java.util.Set.of());

    private static CurrentUser lecturer(UUID userId) {
        return new CurrentUser(
                userId,
                "subject-lecturer",
                "lecturer",
                "lecturer@university.test",
                "Lee Lecturer",
                java.util.Optional.empty(),
                java.util.Set.of(SecurityRoles.LECTURER),
                java.util.Set.of());
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("enrols the student and takes a seat when everything checks out")
    void enrolsSuccessfully() {
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        when(courseCatalog.tryReserveSeat(SECTION_ID)).thenReturn(true);
        when(repository.saveAndFlush(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = service.enrol(request);

        assertThat(response.studentId()).isEqualTo(STUDENT_ID);
        assertThat(response.courseSectionId()).isEqualTo(SECTION_ID);
        assertThat(response.status()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(response.attemptNumber()).isEqualTo(1);
        verify(courseCatalog).tryReserveSeat(SECTION_ID);
        verify(studentBilling)
                .chargeForEnrolment(eq(STUDENT_ID), any(), eq(TERM_ID), eq("COMP3101"), eq(COURSE_ID), eq(3), isNull());
        verify(auditTrail)
                .record(
                        eq(STAFF_CALLER.userId()),
                        eq("Rita Registrar"),
                        eq(AuditTrail.Action.ENROLMENT_CREATED),
                        eq(AuditTrail.EntityType.ENROLLMENT),
                        any(),
                        contains("COMP3101"));
    }

    @Test
    @DisplayName("a later sit of the same course is attempt 2")
    void secondSitOfSameCourseIsAttemptTwo() {
        UUID priorSectionId = UUID.randomUUID();
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        Enrollment prior = new Enrollment(
                STUDENT_ID, priorSectionId, EnrollmentStatus.COMPLETED, null, 1);
        when(repository.findByStudentIdAndStatusIn(eq(STUDENT_ID), any())).thenReturn(List.of(prior));
        when(courseCatalog.findSection(priorSectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        priorSectionId, COURSE_ID, "COMP3101", "Course", TERM_ID, "B", 30, 5, true, null, false)));
        when(courseCatalog.tryReserveSeat(SECTION_ID)).thenReturn(true);
        when(repository.saveAndFlush(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = service.enrol(request);

        assertThat(response.attemptNumber()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Concurrency
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a full section waitlists the student instead of returning 409")
    void waitlistsWhenSectionIsFull() {
        givenEligibleStudent();
        givenOpenSection(30, 30);
        givenRegistrationOpen();
        when(courseCatalog.tryReserveSeat(SECTION_ID)).thenReturn(false);
        when(repository.saveAndFlush(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = service.enrol(request);

        assertThat(response.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
        verify(studentBilling, never())
                .chargeForEnrolment(any(), any(), any(), any(), any(), anyInt(), any());
        verify(metrics).enrolment("waitlisted");
    }

    @Test
    @DisplayName("losing the unique-index race is reported as a duplicate, not a 500")
    void translatesConcurrentDuplicateIntoConflict() {
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        // Both requests passed the existence check; the database is what separates them.
        when(courseCatalog.tryReserveSeat(SECTION_ID)).thenReturn(true);
        when(repository.saveAndFlush(any(Enrollment.class)))
                .thenThrow(new DataIntegrityViolationException("uk_enrollments_student_section_active"));

        assertThatThrownBy(() -> service.enrol(request))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .satisfies(thrown -> assertThat(((ResourceAlreadyExistsException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("a billing constraint failure is not reported as already registered")
    void billingFailureIsNotReportedAsADuplicate() {
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        when(courseCatalog.tryReserveSeat(SECTION_ID)).thenReturn(true);
        when(repository.saveAndFlush(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new DataIntegrityViolationException("value too long for type character varying(80)"))
                .when(studentBilling)
                .chargeForEnrolment(any(), any(), any(), any(), any(), anyInt(), any());

        assertThatThrownBy(() -> service.enrol(request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("value too long");
    }

    @Test
    @DisplayName("an already-enrolled student is rejected before a seat is taken")
    void rejectsKnownDuplicateWithoutReservingASeat() {
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        when(repository.findByStudentIdAndCourseSectionIdAndStatusIn(eq(STUDENT_ID), eq(SECTION_ID), any()))
                .thenReturn(List.of(new Enrollment(STUDENT_ID, SECTION_ID)));

        assertThatThrownBy(() -> service.enrol(request)).isInstanceOf(ResourceAlreadyExistsException.class);

        verify(courseCatalog, never()).tryReserveSeat(any());
    }

    @Test
    @DisplayName("checkout skips a course the student is already registered for")
    void checkoutSkipsCoursesAlreadyOnTheBooks() {
        givenEligibleStudent();
        givenOpenSection(30, 10);
        when(repository.findByStudentIdAndCourseSectionIdAndStatusIn(eq(STUDENT_ID), eq(SECTION_ID), any()))
                .thenReturn(List.of(new Enrollment(STUDENT_ID, SECTION_ID)));

        var response = service.checkout(new CheckoutEnrollmentsRequest(STUDENT_ID, List.of(SECTION_ID)));

        assertThat(response.enrollments()).hasSize(1);
        assertThat(response.enrollments().get(0).status()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(response.checkoutBatchId()).isNull();
        verify(courseCatalog, never()).tryReserveSeat(any());
    }

    @Test
    @DisplayName("checkout stamps a batch the student can undo")
    void checkoutStampsACorrectionBatch() {
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        when(academicStructure.creditLoadFor(any())).thenReturn(new AcademicStructure.CreditLoad(3, 18, false));
        when(courseCatalog.tryReserveSeat(SECTION_ID)).thenReturn(true);
        when(repository.saveAndFlush(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.checkout(new CheckoutEnrollmentsRequest(STUDENT_ID, List.of(SECTION_ID)));

        assertThat(response.checkoutBatchId()).isNotNull();
        assertThat(response.correctionExpiresAt()).isNotNull();
        assertThat(response.enrollments()).hasSize(1);
    }

    @Test
    @DisplayName("undoing a checkout drops every course from that confirmation")
    void undoesAnOpenCheckoutWhileTheWindowAndHoursRemain() {
        givenOpenSection(30, 10);
        UUID batch = UUID.randomUUID();
        Enrollment row = new Enrollment(STUDENT_ID, SECTION_ID, EnrollmentStatus.ENROLLED, batch);
        givenStudentCaller();
        when(repository.findByStudentIdAndCheckoutBatchId(STUDENT_ID, batch)).thenReturn(List.of(row));
        when(repository.findById(row.getId())).thenReturn(Optional.of(row));
        when(academicStructure.canDropWithoutPenalty(eq(TERM_ID), any(Instant.class))).thenReturn(true);

        var response = service.undoOwnCheckout(batch);

        assertThat(response.enrollments()).hasSize(1);
        assertThat(row.getStatus()).isEqualTo(EnrollmentStatus.DROPPED);
        verify(courseCatalog).releaseSeat(SECTION_ID);
        verify(metrics).enrolment("checkout_undone");
    }

    @Test
    @DisplayName("undo is refused after the university correction hours")
    void refusesUndoAfterCorrectionHoursElapse() throws Exception {
        UUID batch = UUID.randomUUID();
        Enrollment row = new Enrollment(STUDENT_ID, SECTION_ID, EnrollmentStatus.ENROLLED, batch);
        var enrolledAt = Enrollment.class.getDeclaredField("enrolledAt");
        enrolledAt.setAccessible(true);
        enrolledAt.set(row, Instant.now().minus(java.time.Duration.ofHours(49)));
        givenStudentCaller();
        when(repository.findByStudentIdAndCheckoutBatchId(STUDENT_ID, batch)).thenReturn(List.of(row));

        assertThatThrownBy(() -> service.undoOwnCheckout(batch))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_CORRECTION_CLOSED));
        verify(courseCatalog, never()).releaseSeat(any());
    }

    @Test
    @DisplayName("undo is refused when registration and add/drop have closed")
    void refusesUndoWhenTheTermWindowHasClosed() {
        givenOpenSection(30, 10);
        UUID batch = UUID.randomUUID();
        Enrollment row = new Enrollment(STUDENT_ID, SECTION_ID, EnrollmentStatus.ENROLLED, batch);
        givenStudentCaller();
        when(repository.findByStudentIdAndCheckoutBatchId(STUDENT_ID, batch)).thenReturn(List.of(row));
        when(academicStructure.canDropWithoutPenalty(eq(TERM_ID), any(Instant.class))).thenReturn(false);

        assertThatThrownBy(() -> service.undoOwnCheckout(batch))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_REGISTRATION_CLOSED));
        verify(courseCatalog, never()).releaseSeat(any());
    }

    @Test
    @DisplayName("unmet course requirements are refused before a seat is taken")
    void rejectsUnmetPrerequisitesWithoutReservingASeat() {
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        when(courseCatalog.unmetRequirements(eq(COURSE_ID), any(), any(), anyInt()))
                .thenReturn(List.of("Prerequisite: CMP1024"));

        assertThatThrownBy(() -> service.enrol(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> {
                    BusinessException ex = (BusinessException) thrown;
                    assertThat(ex.getErrorCode()).isEqualTo(EnrollmentErrorCode.ENROLLMENT_PREREQUISITE_NOT_MET);
                    assertThat(ex.getMessage()).contains("CMP1024");
                });

        verify(courseCatalog, never()).tryReserveSeat(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a dropped section can be selected again this term")
    void enrolsAgainAfterADrop() {
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        when(courseCatalog.tryReserveSeat(SECTION_ID)).thenReturn(true);
        when(repository.saveAndFlush(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = service.enrol(request);

        assertThat(response.status()).isEqualTo(EnrollmentStatus.ENROLLED);
        verify(courseCatalog).tryReserveSeat(SECTION_ID);
    }

    @Test
    @DisplayName("enrolment that would exceed the programme maximum is refused")
    void refusesWhenCreditLoadWouldExceedTheMaximum() {
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        when(academicStructure.creditLoadFor(any())).thenReturn(new AcademicStructure.CreditLoad(12, 3, true));
        Enrollment existing = new Enrollment(STUDENT_ID, UUID.randomUUID());
        when(repository.findByStudentIdAndStatusIn(eq(STUDENT_ID), any())).thenReturn(List.of(existing));
        UUID otherSection = existing.getCourseSectionId();
        when(courseCatalog.findSection(otherSection))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        otherSection, COURSE_ID, "COMP3101", "Course", TERM_ID, "B", 30, 10, true, null, false)));

        assertThatThrownBy(() -> service.enrol(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_CREDIT_LOAD_EXCEEDED));

        verify(courseCatalog, never()).tryReserveSeat(any());
    }

    @Test
    @DisplayName("an occurrence that overlaps another enrolment this term is refused")
    void refusesWhenTimetableClashes() {
        UUID otherSection = UUID.randomUUID();
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        Enrollment held = new Enrollment(STUDENT_ID, otherSection);
        when(repository.findByStudentIdAndStatusIn(eq(STUDENT_ID), any())).thenReturn(List.of(held));
        when(courseCatalog.findSection(otherSection))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        otherSection, UUID.randomUUID(), "MATH1150", "Course", TERM_ID, "UN1", 30, 10, true, null, false)));
        when(courseCatalog.meetingsOf(SECTION_ID))
                .thenReturn(List.of(new CourseCatalog.Meeting(1, "Mon", "09:00", "10:00", "A1", "Lecture")));
        when(courseCatalog.meetingsOf(otherSection))
                .thenReturn(List.of(new CourseCatalog.Meeting(1, "Mon", "09:30", "10:30", "B1", "Lecture")));

        assertThatThrownBy(() -> service.enrol(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_TIMETABLE_CLASH));

        verify(courseCatalog, never()).tryReserveSeat(any());
    }

    @Test
    @DisplayName("a course off the programme map is refused")
    void refusesWhenCourseIsNotOnTheProgramme() {
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        when(curriculumCatalog.allowsEnrolment(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.enrol(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_NOT_ON_PROGRAMME));

        verify(courseCatalog, never()).tryReserveSeat(any());
    }

    @Test
    @DisplayName("checkout refuses a cart below the programme minimum")
    void checkoutRefusesWhenBelowMinimumCredits() {
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();

        assertThatThrownBy(() -> service.checkout(new CheckoutEnrollmentsRequest(STUDENT_ID, List.of(SECTION_ID))))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_CREDIT_LOAD_BELOW_MINIMUM));

        verify(courseCatalog, never()).tryReserveSeat(any());
    }

    @Test
    @DisplayName("dropping a seat promotes the oldest waitlisted student")
    void dropPromotesTheWaitlist() {
        UUID waitingStudent = UUID.randomUUID();
        Enrollment enrolment = new Enrollment(STUDENT_ID, SECTION_ID);
        Enrollment waiting = new Enrollment(waitingStudent, SECTION_ID, EnrollmentStatus.WAITLISTED);
        when(repository.findById(enrolment.getId())).thenReturn(Optional.of(enrolment));
        givenOpenSection(30, 10);
        when(academicStructure.canDropWithoutPenalty(eq(TERM_ID), any(Instant.class))).thenReturn(true);
        when(repository.findByCourseSectionIdAndStatusOrderByEnrolledAtAsc(SECTION_ID, EnrollmentStatus.WAITLISTED))
                .thenReturn(List.of(waiting));
        when(courseCatalog.tryReserveSeat(SECTION_ID)).thenReturn(true);

        EnrollmentResponse response = service.drop(enrolment.getId());

        assertThat(response.status()).isEqualTo(EnrollmentStatus.DROPPED);
        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        verify(courseCatalog).releaseSeat(SECTION_ID);
        verify(courseCatalog).tryReserveSeat(SECTION_ID);
        verify(studentBilling)
                .chargeForEnrolment(eq(waitingStudent), any(), eq(TERM_ID), eq("COMP3101"), eq(COURSE_ID), eq(3), isNull());
        verify(metrics).enrolment("promoted");
    }

    // ------------------------------------------------------------------
    // Guard rails
    // ------------------------------------------------------------------

    @Test
    void rejectsUnknownStudent() {
        when(studentDirectory.findById(STUDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enrol(request)).isInstanceOf(ResourceNotFoundException.class);
        verify(courseCatalog, never()).tryReserveSeat(any());
    }

    @Test
    @DisplayName("a student not in good standing cannot register")
    void rejectsIneligibleStudent() {
        when(studentDirectory.findById(STUDENT_ID))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        STUDENT_ID, UUID.randomUUID(), "20260001", UUID.randomUUID(), null, false, ResidencyClassification.IN_DISTRICT)));

        assertThatThrownBy(() -> service.enrol(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_STUDENT_NOT_ELIGIBLE));
    }

    @Test
    void rejectsClosedSection() {
        givenEligibleStudent();
        when(courseCatalog.findSection(SECTION_ID))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        SECTION_ID, UUID.randomUUID(), "COMP3101", "Course", TERM_ID, "A", 30, 5, false, null, false)));

        assertThatThrownBy(() -> service.enrol(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_SECTION_NOT_OPEN));
    }

    @Test
    @DisplayName("an open section outside the registration window is still refused")
    void rejectsWhenRegistrationWindowIsClosed() {
        givenEligibleStudent();
        givenOpenSection(30, 5);
        when(academicStructure.canAddEnrolment(eq(TERM_ID), any(Instant.class))).thenReturn(false);

        assertThatThrownBy(() -> service.enrol(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_REGISTRATION_CLOSED));

        verify(courseCatalog, never()).tryReserveSeat(any());
    }

    // ------------------------------------------------------------------
    // Ending an enrolment
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dropping returns the seat to the section")
    void dropReleasesTheSeat() {
        Enrollment enrolment = new Enrollment(STUDENT_ID, SECTION_ID);
        when(repository.findById(enrolment.getId())).thenReturn(Optional.of(enrolment));

        EnrollmentResponse response = service.drop(enrolment.getId());

        assertThat(response.status()).isEqualTo(EnrollmentStatus.DROPPED);
        verify(courseCatalog).releaseSeat(SECTION_ID);
    }

    @Test
    @DisplayName("dropping after add/drop is refused so the student must withdraw")
    void refusesDropWhenAddDropHasClosed() {
        Enrollment enrolment = new Enrollment(STUDENT_ID, SECTION_ID);
        when(repository.findById(enrolment.getId())).thenReturn(Optional.of(enrolment));
        givenOpenSection(30, 5);
        when(academicStructure.canDropWithoutPenalty(eq(TERM_ID), any(Instant.class))).thenReturn(false);

        assertThatThrownBy(() -> service.drop(enrolment.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_ADD_DROP_CLOSED));

        verify(courseCatalog, never()).releaseSeat(any());
    }

    @Test
    @DisplayName("E4: withdrawing after add/drop has closed credits the tapering refund, not the full drop credit")
    void withdrawingCallsCreditForWithdrawalNotCreditForDrop() {
        Enrollment enrolment = new Enrollment(STUDENT_ID, SECTION_ID);
        when(repository.findById(enrolment.getId())).thenReturn(Optional.of(enrolment));
        givenOpenSection(30, 5);
        when(academicStructure.canDropWithoutPenalty(eq(TERM_ID), any(Instant.class))).thenReturn(false);

        EnrollmentResponse response = service.withdraw(enrolment.getId());

        assertThat(response.status()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        verify(studentBilling).creditForWithdrawal(eq(STUDENT_ID), eq(enrolment.getId()), eq(TERM_ID), any(), anyBoolean());
        verify(studentBilling, never()).creditForDrop(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("dropping twice is idempotent and returns the seat only once")
    void doubleDropDoesNotDoubleRelease() {
        Enrollment enrolment = new Enrollment(STUDENT_ID, SECTION_ID);
        enrolment.transitionTo(EnrollmentStatus.DROPPED);
        when(repository.findById(enrolment.getId())).thenReturn(Optional.of(enrolment));

        // Re-dropping is a no-op rather than an error: a client retrying after a timeout must not
        // be punished for it. The seat is not returned a second time because the enrolment was no
        // longer holding one, which is the property that actually matters for capacity.
        EnrollmentResponse response = service.drop(enrolment.getId());

        assertThat(response.status()).isEqualTo(EnrollmentStatus.DROPPED);
        verify(courseCatalog, never()).releaseSeat(any());
    }

    @Test
    @DisplayName("a genuinely forbidden transition is refused")
    void refusesForbiddenTransition() {
        Enrollment enrolment = new Enrollment(STUDENT_ID, SECTION_ID);
        enrolment.transitionTo(EnrollmentStatus.DROPPED);
        when(repository.findById(enrolment.getId())).thenReturn(Optional.of(enrolment));

        // DROPPED is terminal — it cannot be resurrected into a completed course.
        assertThatThrownBy(() -> service.complete(enrolment.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.INVALID_ENROLLMENT_STATE));
    }

    @Test
    @DisplayName("the assigned lecturer may complete an enrolment in their section")
    void assignedLecturerMayComplete() {
        UUID lecturerId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(lecturer(lecturerId));
        Enrollment enrolment = new Enrollment(STUDENT_ID, SECTION_ID);
        when(repository.findById(enrolment.getId())).thenReturn(Optional.of(enrolment));
        when(courseCatalog.teaches(lecturerId, SECTION_ID)).thenReturn(true);

        EnrollmentResponse response = service.complete(enrolment.getId());

        assertThat(response.status()).isEqualTo(EnrollmentStatus.COMPLETED);
    }

    @Test
    @DisplayName("a lecturer may NOT complete an enrolment in someone else's section")
    void otherLecturerMayNotComplete() {
        UUID lecturerId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(lecturer(lecturerId));
        Enrollment enrolment = new Enrollment(STUDENT_ID, SECTION_ID);
        when(repository.findById(enrolment.getId())).thenReturn(Optional.of(enrolment));
        when(courseCatalog.teaches(lecturerId, SECTION_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.complete(enrolment.getId()))
                .isInstanceOf(ForbiddenException.class);
        assertThat(enrolment.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    @DisplayName("the registry may complete an enrolment they do not teach")
    void registrarMayCompleteAnySection() {
        Enrollment enrolment = new Enrollment(STUDENT_ID, SECTION_ID);
        when(repository.findById(enrolment.getId())).thenReturn(Optional.of(enrolment));

        EnrollmentResponse response = service.complete(enrolment.getId());

        assertThat(response.status()).isEqualTo(EnrollmentStatus.COMPLETED);
        verify(courseCatalog, never()).teaches(any(), any());
    }

    @Test
    @DisplayName("complete() is refused when the section has no published overall result")
    void completeIsRefusedWithoutAPublishedResult() {
        when(currentUserProvider.require()).thenReturn(STAFF_CALLER);
        Enrollment enrolment = new Enrollment(STUDENT_ID, SECTION_ID);
        when(repository.findById(enrolment.getId())).thenReturn(Optional.of(enrolment));
        when(curriculumCatalog.hasPublishedResult(STUDENT_ID, SECTION_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.complete(enrolment.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(EnrollmentErrorCode.ENROLLMENT_NO_PUBLISHED_RESULT));
    }

    @Test
    @DisplayName("a completed but failed prerequisite course does not satisfy the prerequisite")
    void failedCompletedCourseDoesNotSatisfyItsOwnPrerequisite() {
        UUID prereqSectionId = UUID.randomUUID();
        UUID prereqCourseId = UUID.randomUUID();
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        Enrollment priorAttempt = new Enrollment(STUDENT_ID, prereqSectionId, EnrollmentStatus.COMPLETED);
        when(repository.findByStudentIdAndStatusIn(eq(STUDENT_ID), any())).thenReturn(List.of(priorAttempt));
        CourseCatalog.SectionSummary prereqSection = new CourseCatalog.SectionSummary(
                prereqSectionId, prereqCourseId, "CMP1024", "Foundations", TERM_ID, "A", 30, 10, true, null, false);
        CourseCatalog.CourseSummary prereqCourse =
                new CourseCatalog.CourseSummary(prereqCourseId, "CMP1024", "Foundations", 3, 1, true);
        when(courseCatalog.findSections(any())).thenReturn(List.of(prereqSection));
        when(courseCatalog.findCourses(any())).thenReturn(List.of(prereqCourse));
        when(curriculumCatalog.hasPassed(STUDENT_ID, prereqCourseId)).thenReturn(false);

        ArgumentCaptor<Set<UUID>> completedCaptor = ArgumentCaptor.forClass(Set.class);
        when(courseCatalog.unmetRequirements(eq(COURSE_ID), completedCaptor.capture(), any(), anyInt()))
                .thenReturn(List.of());
        when(repository.saveAndFlush(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enrol(request);

        assertThat(completedCaptor.getValue()).doesNotContain(prereqCourseId);
    }

    @Test
    @DisplayName(
            "G2: transfer credit for a prerequisite the student never enrolled in internally still satisfies it")
    void transferCreditWithNoEnrolmentHistorySatisfiesThePrerequisite() {
        UUID prereqCourseId = UUID.randomUUID();
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        // No enrolment history at all — the credit came from another institution, so the loop that
        // builds "completed" from Enrollment rows has nothing to find on its own.
        when(repository.findByStudentIdAndStatusIn(eq(STUDENT_ID), any())).thenReturn(List.of());
        when(curriculumCatalog.transferCreditedCourseIds(STUDENT_ID)).thenReturn(Set.of(prereqCourseId));
        CourseCatalog.CourseSummary prereqCourse =
                new CourseCatalog.CourseSummary(prereqCourseId, "CMP1024", "Foundations", 3, 1, true);
        when(courseCatalog.findCourses(any())).thenAnswer(invocation -> {
            java.util.Collection<UUID> ids = invocation.getArgument(0);
            return ids.contains(prereqCourseId) ? List.of(prereqCourse) : List.of();
        });

        ArgumentCaptor<Set<UUID>> completedCaptor = ArgumentCaptor.forClass(Set.class);
        when(courseCatalog.unmetRequirements(eq(COURSE_ID), completedCaptor.capture(), any(), anyInt()))
                .thenReturn(List.of());
        when(repository.saveAndFlush(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enrol(request);

        assertThat(completedCaptor.getValue()).contains(prereqCourseId);
    }

    @Test
    @DisplayName("F6: checking prerequisites batches the catalog lookups instead of one per historical enrolment")
    void prerequisiteCheckBatchesCatalogLookups() {
        UUID sectionA = UUID.randomUUID();
        UUID sectionB = UUID.randomUUID();
        UUID sectionC = UUID.randomUUID();
        UUID courseA = UUID.randomUUID();
        UUID courseB = UUID.randomUUID();
        UUID courseC = UUID.randomUUID();
        givenEligibleStudent();
        givenOpenSection(30, 10);
        givenRegistrationOpen();
        when(repository.findByStudentIdAndStatusIn(eq(STUDENT_ID), any()))
                .thenReturn(List.of(
                        new Enrollment(STUDENT_ID, sectionA, EnrollmentStatus.COMPLETED),
                        new Enrollment(STUDENT_ID, sectionB, EnrollmentStatus.COMPLETED),
                        new Enrollment(STUDENT_ID, sectionC, EnrollmentStatus.ENROLLED)));
        when(courseCatalog.findSections(any()))
                .thenReturn(List.of(
                        new CourseCatalog.SectionSummary(
                                sectionA, courseA, "CMP1024", "A", TERM_ID, "A", 30, 10, true, null, false),
                        new CourseCatalog.SectionSummary(
                                sectionB, courseB, "CMP1025", "B", TERM_ID, "A", 30, 10, true, null, false),
                        new CourseCatalog.SectionSummary(
                                sectionC, courseC, "CMP1026", "C", TERM_ID, "A", 30, 10, true, null, false)));
        when(courseCatalog.findCourses(any()))
                .thenReturn(List.of(
                        new CourseCatalog.CourseSummary(courseA, "CMP1024", "A", 3, 1, true),
                        new CourseCatalog.CourseSummary(courseB, "CMP1025", "B", 3, 1, true),
                        new CourseCatalog.CourseSummary(courseC, "CMP1026", "C", 3, 1, true)));
        when(curriculumCatalog.hasPassed(eq(STUDENT_ID), any())).thenReturn(true);
        when(courseCatalog.unmetRequirements(eq(COURSE_ID), any(), any(), anyInt())).thenReturn(List.of());
        when(repository.saveAndFlush(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enrol(request);

        // Exactly one batch call for the whole prerequisite check, not one per historical row —
        // findSection/findCourse are still legitimately called elsewhere in enrol() for unrelated
        // per-section lookups (credit load, attempt numbering), so this only asserts the specific
        // fix: the history loop batches instead of looping single lookups.
        verify(courseCatalog, times(1)).findSections(any());
        verify(courseCatalog, times(1)).findCourses(any());
    }

    @Test
    void unknownEnrolmentIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(unknown)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("A7: staff searching a named student's enrolments is logged as a FERPA disclosure")
    void staffSearchingAStudentsEnrolmentsIsLogged() {
        when(currentUserProvider.require()).thenReturn(STAFF_CALLER);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(repository.search(STUDENT_ID, null, null, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.search(STUDENT_ID, null, null, pageable);

        verify(recordAccessLog)
                .record(
                        STAFF_CALLER.userId(),
                        STAFF_CALLER.fullName(),
                        STUDENT_ID,
                        RecordAccessLog.RecordType.ENROLLMENT,
                        RecordAccessLog.Action.VIEW,
                        "Enrolment history");
    }

    @Test
    @DisplayName("A7: a staff-wide search with no named student is not logged as a disclosure")
    void staffBrowsingWithoutAStudentFilterIsNotLogged() {
        when(currentUserProvider.require()).thenReturn(STAFF_CALLER);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(repository.search(null, SECTION_ID, null, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.search(null, SECTION_ID, null, pageable);

        verify(recordAccessLog, never())
                .record(any(UUID.class), any(), any(UUID.class), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private void givenEligibleStudent() {
        when(studentDirectory.findById(STUDENT_ID))
                .thenReturn(Optional.of(
                        new StudentDirectory.StudentSummary(
                                STUDENT_ID, UUID.randomUUID(), "20260001", UUID.randomUUID(), null, true, ResidencyClassification.IN_DISTRICT)));
    }

    private void givenOpenSection(int capacity, int enrolled) {
        when(courseCatalog.findSection(SECTION_ID))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        SECTION_ID, COURSE_ID, "COMP3101", "Course", TERM_ID, "A", capacity, enrolled, true, null, false)));
    }

    private void givenRegistrationOpen() {
        when(academicStructure.canAddEnrolment(eq(TERM_ID), any(Instant.class))).thenReturn(true);
    }

    private void givenStudentCaller() {
        when(currentUserProvider.require()).thenReturn(STUDENT_CALLER);
        when(studentDirectory.studentIdOfUser(USER_ID)).thenReturn(Optional.of(STUDENT_ID));
    }
}
