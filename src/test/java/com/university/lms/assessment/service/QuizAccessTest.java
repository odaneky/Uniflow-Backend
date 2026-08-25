package com.university.lms.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.university.lms.assessment.domain.Assessment;
import com.university.lms.assessment.domain.AssessmentType;
import com.university.lms.assessment.repository.AssessmentAttemptRepository;
import com.university.lms.assessment.repository.AssessmentRepository;
import com.university.lms.assessment.repository.QuizAnswerRepository;
import com.university.lms.assessment.repository.QuizOptionRepository;
import com.university.lms.assessment.repository.QuizQuestionRepository;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.finance.api.StudentBilling;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A5: {@code QuizService.requireTeacherOrAdmin} — QuizService's own independent copy of the same
 * guard shape — let SYSTEM_ADMIN/REGISTRAR/FACULTY_ADMIN bypass section-department scoping
 * unconditionally, the same over-reach already fixed in {@code AssessmentService}, {@code
 * LearningService}, {@code GradeService} and {@code AttendanceService}. Same fail-open safety
 * property throughout; LECTURER's "must be this section's own lecturer" rule is untouched.
 */
@ExtendWith(MockitoExtension.class)
class QuizAccessTest {

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private AssessmentAttemptRepository attemptRepository;

    @Mock
    private QuizQuestionRepository questionRepository;

    @Mock
    private QuizOptionRepository optionRepository;

    @Mock
    private QuizAnswerRepository answerRepository;

    @Mock
    private CourseCatalog courseCatalog;

    @Mock
    private EnrollmentDirectory enrollmentDirectory;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private DocumentStore documentStore;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private StudentBilling studentBilling;

    @Mock
    private StaffAppointments staffAppointments;

    private QuizService service;

    private static final UUID SECTION_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID ORG_UNIT_ID = UUID.randomUUID();
    private static final UUID ASSESSMENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new QuizService(
                assessmentRepository,
                attemptRepository,
                questionRepository,
                optionRepository,
                answerRepository,
                courseCatalog,
                enrollmentDirectory,
                studentDirectory,
                userDirectory,
                documentStore,
                currentUserProvider,
                studentBilling,
                staffAppointments);

        Assessment assessment =
                new Assessment(SECTION_ID, "Midterm", AssessmentType.QUIZ, new BigDecimal("100"), new BigDecimal("20"));
        when(assessmentRepository.findById(ASSESSMENT_ID)).thenReturn(Optional.of(assessment));

        CourseCatalog.SectionSummary section = new CourseCatalog.SectionSummary(
                SECTION_ID, COURSE_ID, "CMP1024", "Foundations", UUID.randomUUID(), "A", 30, 10, true, null, false);
        when(courseCatalog.findSection(SECTION_ID)).thenReturn(Optional.of(section));
        org.mockito.Mockito.lenient()
                .when(questionRepository.findByAssessmentIdOrderByPositionAsc(any()))
                .thenReturn(List.of());
    }

    private static CurrentUser callerWithRole(String role) {
        return new CurrentUser(
                UUID.randomUUID(), "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(role), Set.of());
    }

    @Test
    @DisplayName("fails open: a registrar caller with no appointment at all is authorized")
    void failsOpenWithNoAppointmentData() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.REGISTRAR));
        when(staffAppointments.activeAppointmentsOf(any())).thenReturn(List.of());

        assertThat(service.structureForStaff(ASSESSMENT_ID)).isNotNull();
    }

    @Test
    @DisplayName("real narrowing: a registrar appointed elsewhere is refused")
    void refusesRegistrarAppointedElsewhere() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.REGISTRAR), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(UUID.randomUUID(), "OTHER-DEPT", "REGISTRAR")));
        when(courseCatalog.departmentOfCourse(COURSE_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.structureForStaff(ASSESSMENT_ID)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("a faculty admin appointed over this department is authorized")
    void allowsFacultyAdminAppointedOverTheDepartment() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.FACULTY_ADMIN), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(ORG_UNIT_ID, "DEPT:CS", "FACULTY_ADMIN")));
        when(courseCatalog.departmentOfCourse(COURSE_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(true);

        assertThat(service.structureForStaff(ASSESSMENT_ID)).isNotNull();
    }

    @Test
    @DisplayName("SYSTEM_ADMIN always has access, regardless of appointment data")
    void systemAdminAlwaysAuthorized() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.SYSTEM_ADMIN));

        assertThat(service.structureForStaff(ASSESSMENT_ID)).isNotNull();
    }

    @Test
    @DisplayName("a lecturer who does not teach this section is still refused, unchanged")
    void stillRefusesALecturerWhoDoesNotTeachThisSection() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.LECTURER));

        assertThatThrownBy(() -> service.structureForStaff(ASSESSMENT_ID)).isInstanceOf(ForbiddenException.class);
    }
}
