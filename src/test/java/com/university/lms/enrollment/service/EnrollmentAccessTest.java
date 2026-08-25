package com.university.lms.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.api.RecordAccessLog;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.common.telemetry.UniFlowMetrics;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.curriculum.api.CurriculumCatalog;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.dto.EnrollmentResponse;
import com.university.lms.enrollment.repository.EnrollmentCheckoutIdempotencyRepository;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.finance.api.StudentBilling;
import com.university.lms.financialaid.api.RegistrationHolds;
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
 * A5: {@code EnrollmentService.requireOwnStudentRecordOrStaff} narrowed — shared by {@code
 * findById}, {@code drop} and {@code withdraw}, so this fixes all three at once. Same fail-open
 * safety property and student -> programme -> department resolution as {@code
 * StudentService}/{@code FinancialAidService}.
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentAccessTest {

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

    private EnrollmentService service;

    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID SECTION_ID = UUID.randomUUID();
    private static final UUID PROGRAMME_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID ORG_UNIT_ID = UUID.randomUUID();

    private Enrollment enrolment;

    @BeforeEach
    void setUp() {
        service = new EnrollmentService(
                repository,
                checkoutIdempotencyRepository,
                objectMapper,
                studentDirectory,
                courseCatalog,
                curriculumCatalog,
                academicStructure,
                studentBilling,
                registrationHolds,
                currentUserProvider,
                auditTrail,
                recordAccessLog,
                metrics,
                staffAppointments);
        enrolment = new Enrollment(STUDENT_ID, SECTION_ID);
        when(repository.findById(enrolment.getId())).thenReturn(Optional.of(enrolment));
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

        EnrollmentResponse response = service.findById(enrolment.getId());

        assertThat(response).isNotNull();
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

        assertThatThrownBy(() -> service.findById(enrolment.getId())).isInstanceOf(ForbiddenException.class);
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

        EnrollmentResponse response = service.findById(enrolment.getId());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("SYSTEM_ADMIN always has access, regardless of appointment data")
    void systemAdminAlwaysAuthorized() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(UUID.randomUUID(), SecurityRoles.SYSTEM_ADMIN));

        EnrollmentResponse response = service.findById(enrolment.getId());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("a non-staff, non-self caller is refused")
    void nonStaffOtherCallerIsRefused() {
        UUID callerId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(callerWithRole(callerId, SecurityRoles.STUDENT));
        when(studentDirectory.studentIdOfUser(callerId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.findById(enrolment.getId())).isInstanceOf(ForbiddenException.class);
    }
}
