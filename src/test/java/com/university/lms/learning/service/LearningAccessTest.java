package com.university.lms.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.learning.dto.CourseContentResponse;
import com.university.lms.learning.repository.CourseContentRepository;
import com.university.lms.learning.repository.LearningMaterialRepository;
import com.university.lms.learning.repository.LearningModuleRepository;
import com.university.lms.learning.repository.LessonRepository;
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
 * A5: {@code requireEnrolledOrStaff}'s staff branch is the first guard narrowed from a blind {@code
 * isStaff()} to an org-scoped check. These tests pin the safety property that makes it deployable
 * ahead of any environment actually running the provisioning reconcile passes: every "not yet
 * provisioned" state must fail open, exactly as {@code isStaff()} always did — only a caller who
 * has real appointment and org-unit-link data, and is provably appointed elsewhere, is refused.
 */
@ExtendWith(MockitoExtension.class)
class LearningAccessTest {

    @Mock
    private CourseContentRepository contentRepository;

    @Mock
    private LearningModuleRepository moduleRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LearningMaterialRepository materialRepository;

    @Mock
    private CourseCatalog courseCatalog;

    @Mock
    private EnrollmentDirectory enrollmentDirectory;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private StaffAppointments staffAppointments;

    private LearningService service;

    private static final UUID SECTION_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID ORG_UNIT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LearningService(
                contentRepository,
                moduleRepository,
                lessonRepository,
                materialRepository,
                courseCatalog,
                enrollmentDirectory,
                studentDirectory,
                currentUserProvider,
                staffAppointments);

        CourseCatalog.SectionSummary section = new CourseCatalog.SectionSummary(
                SECTION_ID, COURSE_ID, "CMP1024", "Foundations", UUID.randomUUID(), "A", 30, 10, true, null, false);
        when(courseCatalog.findSection(SECTION_ID)).thenReturn(Optional.of(section));
        lenient().when(contentRepository.findByCourseSectionId(SECTION_ID)).thenReturn(Optional.empty());
    }

    private static CurrentUser callerWithRole(String role) {
        return new CurrentUser(
                UUID.randomUUID(),
                "idp-subject",
                "caller",
                "caller@example.edu",
                "Caller",
                Optional.empty(),
                Set.of(role),
                Set.of());
    }

    @Test
    @DisplayName("SYSTEM_ADMIN always has access, regardless of appointment data")
    void systemAdminAlwaysAuthorized() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.SYSTEM_ADMIN));

        CourseContentResponse response = service.ownContent(SECTION_ID);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("fails open: a staff caller with no appointment at all is authorized, exactly as isStaff() always was")
    void noAppointmentDataFailsOpen() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.LECTURER));
        when(staffAppointments.activeAppointmentsOf(any())).thenReturn(List.of());

        CourseContentResponse response = service.ownContent(SECTION_ID);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("fails open: the section's course has no department to resolve")
    void noDepartmentLinkFailsOpen() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.LECTURER));
        when(staffAppointments.activeAppointmentsOf(any()))
                .thenReturn(List.of(new StaffAppointments.Appointment(UUID.randomUUID(), "UNIV", "LECTURER")));
        when(courseCatalog.departmentOfCourse(COURSE_ID)).thenReturn(Optional.empty());

        CourseContentResponse response = service.ownContent(SECTION_ID);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("fails open: the department has not yet been mirrored as an org unit")
    void noOrgUnitLinkFailsOpen() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.LECTURER));
        when(staffAppointments.activeAppointmentsOf(any()))
                .thenReturn(List.of(new StaffAppointments.Appointment(UUID.randomUUID(), "UNIV", "LECTURER")));
        when(courseCatalog.departmentOfCourse(COURSE_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.empty());

        CourseContentResponse response = service.ownContent(SECTION_ID);

        assertThat(response).isNotNull();
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

        assertThatThrownBy(() -> service.ownContent(SECTION_ID)).isInstanceOf(ForbiddenException.class);
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

        CourseContentResponse response = service.ownContent(SECTION_ID);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("a student with no staff role is unaffected by any of this")
    void nonStaffStudentPathIsUnaffected() {
        UUID callerId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.STUDENT), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(studentDirectory.studentIdOfUser(callerId)).thenReturn(Optional.of(studentId));
        when(enrollmentDirectory.canAccessLearning(studentId, SECTION_ID)).thenReturn(true);

        CourseContentResponse response = service.ownContent(SECTION_ID);

        assertThat(response).isNotNull();
    }
}
