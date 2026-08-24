package com.university.lms.staffing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
}
