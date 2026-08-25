package com.university.lms.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.university.lms.assessment.repository.AssessmentAttemptRepository;
import com.university.lms.assessment.repository.AssessmentRepository;
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
 * A5: {@code AssessmentService.requireEnrolledOrStaff} duplicates {@code LearningService}'s guard
 * shape exactly, so it gets the identical org-scoped narrowing. See {@code LearningAccessTest} for
 * the full rationale — these tests pin the same safety property: every "not yet provisioned" state
 * fails open, and only real appointment + org-unit data narrows anything.
 */
@ExtendWith(MockitoExtension.class)
class AssessmentAccessTest {

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private AssessmentAttemptRepository attemptRepository;

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
    private AssessmentOutboxPublisher assessmentOutboxPublisher;

    @Mock
    private StaffAppointments staffAppointments;

    private AssessmentService service;

    private static final UUID SECTION_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID ORG_UNIT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AssessmentService(
                assessmentRepository,
                attemptRepository,
                courseCatalog,
                enrollmentDirectory,
                studentDirectory,
                userDirectory,
                documentStore,
                currentUserProvider,
                studentBilling,
                assessmentOutboxPublisher,
                staffAppointments);

        CourseCatalog.SectionSummary section = new CourseCatalog.SectionSummary(
                SECTION_ID, COURSE_ID, "CMP1024", "Foundations", UUID.randomUUID(), "A", 30, 10, true, null, false);
        when(courseCatalog.findSection(SECTION_ID)).thenReturn(Optional.of(section));
        org.mockito.Mockito.lenient().when(assessmentRepository.findByCourseSectionId(SECTION_ID)).thenReturn(List.of());
    }

    private static CurrentUser callerWithRole(String role) {
        return new CurrentUser(
                UUID.randomUUID(), "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(role), Set.of());
    }

    @Test
    @DisplayName("fails open: a staff caller with no appointment at all is authorized")
    void noAppointmentDataFailsOpen() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.LECTURER));
        when(staffAppointments.activeAppointmentsOf(any())).thenReturn(List.of());

        assertThat(service.own(SECTION_ID)).isEmpty();
    }

    @Test
    @DisplayName("real narrowing: fully provisioned data and an actual appointment elsewhere is refused")
    void fullyProvisionedAndNotAppointedIsRefused() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.LECTURER), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(UUID.randomUUID(), "OTHER-DEPT", "LECTURER")));
        when(courseCatalog.departmentOfCourse(COURSE_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(false);
        when(studentDirectory.studentIdOfUser(callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.own(SECTION_ID)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("real narrowing: fully provisioned data and an actual appointment over this department is authorized")
    void fullyProvisionedAndAppointedIsAuthorized() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.LECTURER), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(ORG_UNIT_ID, "DEPT:CS", "LECTURER")));
        when(courseCatalog.departmentOfCourse(COURSE_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(true);

        assertThat(service.own(SECTION_ID)).isEmpty();
    }

    @Test
    @DisplayName("SYSTEM_ADMIN always has access, regardless of appointment data")
    void systemAdminAlwaysAuthorized() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.SYSTEM_ADMIN));

        assertThat(service.own(SECTION_ID)).isEmpty();
    }

    /**
     * A5: {@code requireTeacherOrAdmin} (the write-side guard behind {@code forSection}, {@code
     * create}, publish and delete) let SYSTEM_ADMIN/REGISTRAR/FACULTY_ADMIN bypass section-department
     * scoping unconditionally — missed when {@code requireEnrolledOrStaff} above was narrowed,
     * since it is a different method with a narrower role set. LECTURER's "must be this section's
     * own lecturer" rule is untouched throughout.
     */
    @Test
    @DisplayName("teacherOrAdmin: fails open when a registrar caller has no appointment at all")
    void teacherOrAdminFailsOpenWithNoAppointmentData() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.REGISTRAR));
        when(staffAppointments.activeAppointmentsOf(any())).thenReturn(List.of());

        assertThat(service.forSection(SECTION_ID)).isEmpty();
    }

    @Test
    @DisplayName("teacherOrAdmin: real narrowing — a registrar appointed elsewhere is refused")
    void teacherOrAdminRefusesRegistrarAppointedElsewhere() {
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

        assertThatThrownBy(() -> service.forSection(SECTION_ID)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("teacherOrAdmin: a faculty admin appointed over this department is authorized")
    void teacherOrAdminAllowsFacultyAdminAppointedOverTheDepartment() {
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

        assertThat(service.forSection(SECTION_ID)).isEmpty();
    }

    @Test
    @DisplayName("teacherOrAdmin: SYSTEM_ADMIN always has access, regardless of appointment data")
    void teacherOrAdminSystemAdminAlwaysAuthorized() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.SYSTEM_ADMIN));

        assertThat(service.forSection(SECTION_ID)).isEmpty();
    }

    @Test
    @DisplayName("teacherOrAdmin: a lecturer who does not teach this section is still refused, unchanged")
    void teacherOrAdminStillRefusesALecturerWhoDoesNotTeachThisSection() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.LECTURER));

        assertThatThrownBy(() -> service.forSection(SECTION_ID)).isInstanceOf(ForbiddenException.class);
    }
}
