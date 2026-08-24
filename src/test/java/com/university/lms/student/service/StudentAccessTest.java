package com.university.lms.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.api.RecordAccessLog;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.domain.Student;
import com.university.lms.student.dto.StudentResponse;
import com.university.lms.student.repository.AdvisingNoteRepository;
import com.university.lms.student.repository.AdvisorOfficeHoursRepository;
import com.university.lms.student.repository.StudentRepository;
import java.time.LocalDate;
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
 * A5: {@code StudentService.findById}/{@code findByStudentNumber} narrowed the staff branch of
 * {@code CurrentUser.requireSelfOrStaff} — a blind, shared "self or any staff role" check that
 * cannot be narrowed in place without touching every other caller, so this replaces its use here
 * with an org-scoped equivalent. Self-access remains unconditional throughout.
 */
@ExtendWith(MockitoExtension.class)
class StudentAccessTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private AcademicStructure academicStructure;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private RecordAccessLog recordAccessLog;

    @Mock
    private StudentProgrammeEnrolmentService programmeEnrolmentService;

    @Mock
    private AuditTrail auditTrail;

    @Mock
    private AdvisorOfficeHoursRepository advisorOfficeHoursRepository;

    @Mock
    private AdvisingNoteRepository advisingNoteRepository;

    @Mock
    private StaffAppointments staffAppointments;

    private StudentService service;

    private static final UUID STUDENT_USER_ID = UUID.randomUUID();
    private static final UUID PROGRAMME_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID ORG_UNIT_ID = UUID.randomUUID();

    private Student student;

    @BeforeEach
    void setUp() {
        service = new StudentService(
                studentRepository,
                userDirectory,
                academicStructure,
                currentUserProvider,
                recordAccessLog,
                programmeEnrolmentService,
                auditTrail,
                advisorOfficeHoursRepository,
                advisingNoteRepository,
                staffAppointments);
        student = new Student(STUDENT_USER_ID, "20260001", PROGRAMME_ID, LocalDate.of(2026, 9, 1));
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
    }

    private static CurrentUser callerWithRole(UUID userId, String role) {
        return new CurrentUser(
                userId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(role), Set.of());
    }

    @Test
    @DisplayName("the student themselves may always read their own record")
    void selfAccessIsAlwaysAuthorized() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(STUDENT_USER_ID, SecurityRoles.STUDENT));

        StudentResponse response = service.findById(student.getId());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("a non-staff, non-self caller is refused")
    void nonStaffOtherCallerIsRefused() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(UUID.randomUUID(), SecurityRoles.STUDENT));

        assertThatThrownBy(() -> service.findById(student.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("fails open: a staff caller with no appointment at all is authorized")
    void noAppointmentDataFailsOpen() {
        UUID callerId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(callerWithRole(callerId, SecurityRoles.ACADEMIC_ADVISOR));
        when(staffAppointments.activeAppointmentsOf(callerId)).thenReturn(List.of());

        StudentResponse response = service.findById(student.getId());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("real narrowing: fully provisioned data and an actual appointment elsewhere is refused")
    void fullyProvisionedAndNotAppointedIsRefused() {
        UUID callerId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(callerWithRole(callerId, SecurityRoles.ACADEMIC_ADVISOR));
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(UUID.randomUUID(), "OTHER-DEPT", "ACADEMIC_ADVISOR")));
        when(academicStructure.departmentOfProgramme(PROGRAMME_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.findById(student.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("real narrowing: fully provisioned data and an actual appointment over this department is authorized")
    void fullyProvisionedAndAppointedIsAuthorized() {
        UUID callerId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(callerWithRole(callerId, SecurityRoles.ACADEMIC_ADVISOR));
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(ORG_UNIT_ID, "DEPT:CS", "ACADEMIC_ADVISOR")));
        when(academicStructure.departmentOfProgramme(PROGRAMME_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(true);

        StudentResponse response = service.findById(student.getId());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("SYSTEM_ADMIN always has access, regardless of appointment data")
    void systemAdminAlwaysAuthorized() {
        UUID callerId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(callerWithRole(callerId, SecurityRoles.SYSTEM_ADMIN));

        StudentResponse response = service.findById(student.getId());

        assertThat(response).isNotNull();
    }
}
