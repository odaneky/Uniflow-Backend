package com.university.lms.financialaid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.financialaid.dto.FinancialAidAwardResponse;
import com.university.lms.financialaid.repository.FinancialAidAwardRepository;
import com.university.lms.financialaid.repository.IsirSnapshotRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
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
 * A5: {@code FinancialAidService.requireOwnStudentOrStaff}'s staff branch narrowed — Title IV
 * award data, among the most sensitive in the system, was viewable by any staff role for any
 * student regardless of department. Same fail-open safety property and student -> programme ->
 * department resolution as {@code StudentService}/{@code DocumentService}.
 */
@ExtendWith(MockitoExtension.class)
class FinancialAidAccessTest {

    @Mock
    private IsirSnapshotRepository isirRepository;

    @Mock
    private FinancialAidAwardRepository awardRepository;

    @Mock
    private StudentAccountRepository accountRepository;

    @Mock
    private AccountEntryRepository entryRepository;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private AcademicStructure academicStructure;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private StaffAppointments staffAppointments;

    @Mock
    private ScholarshipProgrammeService scholarshipProgrammeService;

    private FinancialAidService service;

    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID PROGRAMME_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID ORG_UNIT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FinancialAidService(
                isirRepository,
                awardRepository,
                accountRepository,
                entryRepository,
                studentDirectory,
                academicStructure,
                currentUserProvider,
                staffAppointments,
                scholarshipProgrammeService);
        when(studentDirectory.exists(STUDENT_ID)).thenReturn(true);
        org.mockito.Mockito.lenient()
                .when(awardRepository.findByStudentIdOrderByCreatedAtDesc(STUDENT_ID))
                .thenReturn(List.of());
    }

    private static CurrentUser callerWithRole(UUID userId, String role) {
        return new CurrentUser(
                userId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(role), Set.of());
    }

    @Test
    @DisplayName("fails open: a staff caller with no appointment at all is authorized")
    void noAppointmentDataFailsOpen() {
        UUID callerId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(callerWithRole(callerId, SecurityRoles.REGISTRAR));
        when(staffAppointments.activeAppointmentsOf(callerId)).thenReturn(List.of());

        List<FinancialAidAwardResponse> awards = service.awardsForStudent(STUDENT_ID);

        assertThat(awards).isEmpty();
    }

    @Test
    @DisplayName("real narrowing: fully provisioned data and an actual appointment elsewhere is refused")
    void fullyProvisionedAndNotAppointedIsRefused() {
        UUID callerId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(callerWithRole(callerId, SecurityRoles.REGISTRAR));
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(UUID.randomUUID(), "OTHER-DEPT", "REGISTRAR")));
        when(studentDirectory.findById(STUDENT_ID))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        STUDENT_ID, UUID.randomUUID(), "S12345", PROGRAMME_ID, null, true, null)));
        when(academicStructure.departmentOfProgramme(PROGRAMME_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.awardsForStudent(STUDENT_ID)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("real narrowing: fully provisioned data and an actual appointment over this department is authorized")
    void fullyProvisionedAndAppointedIsAuthorized() {
        UUID callerId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(callerWithRole(callerId, SecurityRoles.REGISTRAR));
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(ORG_UNIT_ID, "DEPT:CS", "REGISTRAR")));
        when(studentDirectory.findById(STUDENT_ID))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        STUDENT_ID, UUID.randomUUID(), "S12345", PROGRAMME_ID, null, true, null)));
        when(academicStructure.departmentOfProgramme(PROGRAMME_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(true);

        List<FinancialAidAwardResponse> awards = service.awardsForStudent(STUDENT_ID);

        assertThat(awards).isEmpty();
    }

    @Test
    @DisplayName("SYSTEM_ADMIN always has access, regardless of appointment data")
    void systemAdminAlwaysAuthorized() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(UUID.randomUUID(), SecurityRoles.SYSTEM_ADMIN));

        List<FinancialAidAwardResponse> awards = service.awardsForStudent(STUDENT_ID);

        assertThat(awards).isEmpty();
    }

    @Test
    @DisplayName("a non-staff caller who is not the student themselves is refused")
    void nonStaffOtherCallerIsRefused() {
        UUID callerId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(callerWithRole(callerId, SecurityRoles.STUDENT));
        when(studentDirectory.studentIdOfUser(callerId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.awardsForStudent(STUDENT_ID)).isInstanceOf(ForbiddenException.class);
    }
}
