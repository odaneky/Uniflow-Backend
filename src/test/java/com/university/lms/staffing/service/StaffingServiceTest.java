package com.university.lms.staffing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.staffing.domain.OrgUnit;
import com.university.lms.staffing.domain.OrgUnitType;
import com.university.lms.staffing.domain.StaffAppointment;
import com.university.lms.staffing.repository.EmployeeRepository;
import com.university.lms.staffing.repository.OrgUnitRepository;
import com.university.lms.staffing.repository.StaffAppointmentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@code isAppointedOver} is the check A5 will eventually replace {@code CurrentUser.isStaff()}
 * with — nothing calls it yet, but it needs to be right before anything does: a bug here either
 * locks out staff who should have access, or lets a FACULTY_ADMIN reach every faculty's records
 * instead of just the one they administer, which is the exact gap A5 exists to close.
 */
@ExtendWith(MockitoExtension.class)
class StaffingServiceTest {

    @Mock
    private OrgUnitRepository orgUnitRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private StaffAppointmentRepository appointmentRepository;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private StaffingService service;

    private static OrgUnit unit(OrgUnit parent, String code, OrgUnitType type, UUID id) {
        OrgUnit unit = new OrgUnit(parent, code, code, type);
        ReflectionTestUtils.setField(unit, "id", id);
        return unit;
    }

    @Test
    @DisplayName("an appointment at the exact org unit authorizes it")
    void directAppointmentAuthorizes() {
        UUID userId = UUID.randomUUID();
        OrgUnit department = unit(null, "DEPT", OrgUnitType.DEPARTMENT, UUID.randomUUID());
        when(appointmentRepository.findByUserId(userId))
                .thenReturn(List.of(new StaffAppointment(userId, department, "FACULTY_ADMIN", LocalDate.of(2020, 1, 1))));
        when(orgUnitRepository.findById(department.getId())).thenReturn(Optional.of(department));

        assertThat(service.isAppointedOver(userId, department.getId())).isTrue();
    }

    @Test
    @DisplayName("an appointment at a FACULTY authorizes every DEPARTMENT beneath it")
    void ancestorAppointmentAuthorizesDescendants() {
        UUID userId = UUID.randomUUID();
        OrgUnit faculty = unit(null, "FAC", OrgUnitType.FACULTY, UUID.randomUUID());
        OrgUnit department = unit(faculty, "DEPT", OrgUnitType.DEPARTMENT, UUID.randomUUID());
        when(appointmentRepository.findByUserId(userId))
                .thenReturn(List.of(new StaffAppointment(userId, faculty, "FACULTY_ADMIN", LocalDate.of(2020, 1, 1))));
        when(orgUnitRepository.findById(department.getId())).thenReturn(Optional.of(department));

        assertThat(service.isAppointedOver(userId, department.getId())).isTrue();
    }

    @Test
    @DisplayName("an appointment at a sibling unit does not authorize this one")
    void siblingAppointmentDoesNotAuthorize() {
        UUID userId = UUID.randomUUID();
        OrgUnit faculty = unit(null, "FAC", OrgUnitType.FACULTY, UUID.randomUUID());
        OrgUnit ownDepartment = unit(faculty, "DEPT-A", OrgUnitType.DEPARTMENT, UUID.randomUUID());
        OrgUnit siblingDepartment = unit(faculty, "DEPT-B", OrgUnitType.DEPARTMENT, UUID.randomUUID());
        when(appointmentRepository.findByUserId(userId))
                .thenReturn(List.of(new StaffAppointment(userId, ownDepartment, "LECTURER", LocalDate.of(2020, 1, 1))));
        when(orgUnitRepository.findById(siblingDepartment.getId())).thenReturn(Optional.of(siblingDepartment));

        assertThat(service.isAppointedOver(userId, siblingDepartment.getId())).isFalse();
    }

    @Test
    @DisplayName("an ended appointment no longer authorizes")
    void endedAppointmentDoesNotAuthorize() {
        UUID userId = UUID.randomUUID();
        OrgUnit department = unit(null, "DEPT", OrgUnitType.DEPARTMENT, UUID.randomUUID());
        StaffAppointment ended = new StaffAppointment(userId, department, "LECTURER", LocalDate.of(2020, 1, 1));
        ended.end(LocalDate.of(2024, 1, 1));
        when(appointmentRepository.findByUserId(userId)).thenReturn(List.of(ended));

        assertThat(service.isAppointedOver(userId, department.getId())).isFalse();
    }

    @Test
    @DisplayName("no appointment at all does not authorize")
    void noAppointmentDoesNotAuthorize() {
        UUID userId = UUID.randomUUID();
        UUID orgUnitId = UUID.randomUUID();
        when(appointmentRepository.findByUserId(userId)).thenReturn(List.of());

        assertThat(service.isAppointedOver(userId, orgUnitId)).isFalse();
    }

    private static OrgUnit institutionRoot() {
        return unit(null, "UNIV", OrgUnitType.INSTITUTION, UUID.randomUUID());
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
    @DisplayName("A5: granting a staff role appoints the user at the institution root")
    void ensureAppointmentCreatesAnInstitutionRootAppointment() {
        UUID userId = UUID.randomUUID();
        OrgUnit root = institutionRoot();
        when(orgUnitRepository.findByCode("UNIV")).thenReturn(Optional.of(root));
        when(appointmentRepository.existsByUserIdAndOrgUnitIdAndRoleAndValidToIsNull(userId, root.getId(), "LECTURER"))
                .thenReturn(false);

        service.ensureAppointment(userId, "LECTURER");

        verify(appointmentRepository).save(any(StaffAppointment.class));
    }

    @Test
    @DisplayName("A5: ensureAppointment is a no-op for STUDENT")
    void ensureAppointmentIgnoresStudentRole() {
        service.ensureAppointment(UUID.randomUUID(), SecurityRoles.STUDENT);

        verify(appointmentRepository, never()).save(any());
        verify(orgUnitRepository, never()).findByCode(any());
    }

    @Test
    @DisplayName("A5: ensureAppointment does not duplicate an appointment that already exists")
    void ensureAppointmentIsIdempotent() {
        UUID userId = UUID.randomUUID();
        OrgUnit root = institutionRoot();
        when(orgUnitRepository.findByCode("UNIV")).thenReturn(Optional.of(root));
        when(appointmentRepository.existsByUserIdAndOrgUnitIdAndRoleAndValidToIsNull(userId, root.getId(), "LECTURER"))
                .thenReturn(true);

        service.ensureAppointment(userId, "LECTURER");

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A5: reconcileAppointments backfills every staff-role holder missing one, and no one else")
    void reconcileAppointmentsBackfillsMissingAppointments() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.SYSTEM_ADMIN));
        OrgUnit root = institutionRoot();
        when(orgUnitRepository.findByCode("UNIV")).thenReturn(Optional.of(root));

        UUID alreadyAppointed = UUID.randomUUID();
        UUID needsAppointment = UUID.randomUUID();
        // Every staff role defaults to "no holders" — only LECTURER is overridden below — so this
        // stays correct as SecurityRoles.STAFF_ROLES grows (A6 added four more roles to it).
        when(userDirectory.findByRealmRole(any())).thenReturn(List.of());
        when(userDirectory.findByRealmRole(SecurityRoles.LECTURER))
                .thenReturn(List.of(
                        new UserDirectory.UserSummary(alreadyAppointed, "a", "A", "a@example.edu", true),
                        new UserDirectory.UserSummary(needsAppointment, "b", "B", "b@example.edu", true)));
        when(appointmentRepository.existsByUserIdAndOrgUnitIdAndRoleAndValidToIsNull(
                        alreadyAppointed, root.getId(), SecurityRoles.LECTURER))
                .thenReturn(true);
        when(appointmentRepository.existsByUserIdAndOrgUnitIdAndRoleAndValidToIsNull(
                        needsAppointment, root.getId(), SecurityRoles.LECTURER))
                .thenReturn(false);

        int created = service.reconcileAppointments();

        assertThat(created).isEqualTo(1);
        verify(appointmentRepository, times(1)).save(any(StaffAppointment.class));
    }

    @Test
    @DisplayName("A5: reconcileAppointments is registry-only")
    void reconcileAppointmentsRefusesNonRegistryCallers() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.LECTURER));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.reconcileAppointments())
                .isInstanceOf(com.university.lms.common.exception.ForbiddenException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A5: ensureOrgUnitFor mirrors a faculty as a namespaced, root-parented org unit")
    void ensureOrgUnitForCreatesANamespacedUnit() {
        UUID facultyId = UUID.randomUUID();
        OrgUnit root = institutionRoot();
        when(orgUnitRepository.existsBySourceTypeAndSourceId("FACULTY", facultyId)).thenReturn(false);
        when(orgUnitRepository.existsByCode("FAC:SCI")).thenReturn(false);
        when(orgUnitRepository.findByCode("UNIV")).thenReturn(Optional.of(root));

        service.ensureOrgUnitFor("FACULTY", facultyId, "SCI", "Science");

        org.mockito.ArgumentCaptor<OrgUnit> captor = org.mockito.ArgumentCaptor.forClass(OrgUnit.class);
        verify(orgUnitRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("FAC:SCI");
        assertThat(captor.getValue().getParent()).isEqualTo(root);
        assertThat(captor.getValue().getUnitType()).isEqualTo(OrgUnitType.FACULTY);
    }

    @Test
    @DisplayName("A5: ensureOrgUnitFor does not duplicate a source already linked")
    void ensureOrgUnitForIsIdempotent() {
        UUID facultyId = UUID.randomUUID();
        when(orgUnitRepository.existsBySourceTypeAndSourceId("FACULTY", facultyId)).thenReturn(true);

        service.ensureOrgUnitFor("FACULTY", facultyId, "SCI", "Science");

        verify(orgUnitRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A5: a faculty and a department may share a code without colliding")
    void facultyAndDepartmentCodesAreNamespacedSeparately() {
        UUID departmentId = UUID.randomUUID();
        OrgUnit root = institutionRoot();
        when(orgUnitRepository.existsBySourceTypeAndSourceId("DEPARTMENT", departmentId)).thenReturn(false);
        when(orgUnitRepository.existsByCode("DEPT:SCI")).thenReturn(false);
        when(orgUnitRepository.findByCode("UNIV")).thenReturn(Optional.of(root));

        service.ensureOrgUnitFor("DEPARTMENT", departmentId, "SCI", "Science Department");

        org.mockito.ArgumentCaptor<OrgUnit> captor = org.mockito.ArgumentCaptor.forClass(OrgUnit.class);
        verify(orgUnitRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("DEPT:SCI");
        assertThat(captor.getValue().getUnitType()).isEqualTo(OrgUnitType.DEPARTMENT);
    }
}
