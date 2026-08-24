package com.university.lms.staffing.service;

import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.staffing.domain.Employee;
import com.university.lms.staffing.domain.OrgUnit;
import com.university.lms.staffing.domain.StaffAppointment;
import com.university.lms.staffing.domain.StaffingErrorCode;
import com.university.lms.staffing.dto.AppointStaffRequest;
import com.university.lms.staffing.dto.CreateOrgUnitRequest;
import com.university.lms.staffing.dto.EmployeeResponse;
import com.university.lms.staffing.dto.OrgUnitResponse;
import com.university.lms.staffing.dto.RegisterEmployeeRequest;
import com.university.lms.staffing.dto.StaffAppointmentResponse;
import com.university.lms.staffing.repository.EmployeeRepository;
import com.university.lms.staffing.repository.OrgUnitRepository;
import com.university.lms.staffing.repository.StaffAppointmentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StaffingService implements StaffAppointments {

    private final OrgUnitRepository orgUnitRepository;
    private final EmployeeRepository employeeRepository;
    private final StaffAppointmentRepository appointmentRepository;
    private final UserDirectory userDirectory;
    private final CurrentUserProvider currentUserProvider;

    public StaffingService(
            OrgUnitRepository orgUnitRepository,
            EmployeeRepository employeeRepository,
            StaffAppointmentRepository appointmentRepository,
            UserDirectory userDirectory,
            CurrentUserProvider currentUserProvider) {
        this.orgUnitRepository = orgUnitRepository;
        this.employeeRepository = employeeRepository;
        this.appointmentRepository = appointmentRepository;
        this.userDirectory = userDirectory;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public OrgUnitResponse createOrgUnit(CreateOrgUnitRequest request) {
        requireRegistry();
        if (orgUnitRepository.existsByCode(request.code())) {
            throw new ResourceAlreadyExistsException(
                    StaffingErrorCode.ORG_UNIT_CODE_EXISTS, "An org unit with code " + request.code() + " already exists");
        }
        OrgUnit parent = null;
        if (request.parentId() != null) {
            parent = orgUnitRepository
                    .findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            StaffingErrorCode.ORG_UNIT_PARENT_NOT_FOUND,
                            "No org unit exists with id " + request.parentId()));
        }
        OrgUnit unit = new OrgUnit(parent, request.code().trim(), request.name().trim(), request.unitType());
        return OrgUnitResponse.from(orgUnitRepository.saveAndFlush(unit));
    }

    public List<OrgUnitResponse> childrenOf(UUID parentId) {
        return orgUnitRepository.findByParentId(parentId).stream().map(OrgUnitResponse::from).toList();
    }

    @Transactional
    public EmployeeResponse registerEmployee(RegisterEmployeeRequest request) {
        requireRegistry();
        if (!userDirectory.exists(request.userId())) {
            throw new ResourceNotFoundException(
                    StaffingErrorCode.EMPLOYEE_USER_NOT_FOUND, "No user exists with id " + request.userId());
        }
        if (employeeRepository.existsByUserId(request.userId())) {
            throw new ResourceAlreadyExistsException(
                    StaffingErrorCode.EMPLOYEE_ALREADY_REGISTERED,
                    "An employee record already exists for user " + request.userId());
        }
        Employee employee = new Employee(
                request.userId(),
                request.employeeNumber(),
                request.contractType(),
                request.fte() == null ? BigDecimal.ONE : request.fte(),
                request.hiredOn());
        return EmployeeResponse.from(employeeRepository.saveAndFlush(employee));
    }

    @Transactional
    public StaffAppointmentResponse appointStaff(AppointStaffRequest request) {
        requireRegistry();
        if (!userDirectory.exists(request.userId())) {
            throw new ResourceNotFoundException(
                    StaffingErrorCode.STAFF_APPOINTMENT_USER_NOT_FOUND, "No user exists with id " + request.userId());
        }
        OrgUnit orgUnit = orgUnitRepository
                .findById(request.orgUnitId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        StaffingErrorCode.ORG_UNIT_NOT_FOUND, "No org unit exists with id " + request.orgUnitId()));
        StaffAppointment appointment = new StaffAppointment(request.userId(), orgUnit, request.role(), request.validFrom());
        return StaffAppointmentResponse.from(appointmentRepository.saveAndFlush(appointment));
    }

    @Transactional
    public void endAppointment(UUID appointmentId, LocalDate validTo) {
        requireRegistry();
        StaffAppointment appointment = appointmentRepository
                .findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StaffingErrorCode.STAFF_APPOINTMENT_NOT_FOUND,
                        "No staff appointment exists with id " + appointmentId));
        appointment.end(validTo);
    }

    public List<StaffAppointmentResponse> appointmentsOf(UUID userId) {
        return appointmentRepository.findByUserId(userId).stream().map(StaffAppointmentResponse::from).toList();
    }

    @Override
    public List<Appointment> activeAppointmentsOf(UUID userId) {
        LocalDate today = LocalDate.now();
        return appointmentRepository.findByUserId(userId).stream()
                .filter(appointment -> appointment.isActiveOn(today))
                .map(appointment ->
                        new Appointment(appointment.getOrgUnit().getId(), appointment.getOrgUnit().getCode(), appointment.getRole()))
                .toList();
    }

    @Override
    public boolean isAppointedOver(UUID userId, UUID orgUnitId) {
        Set<UUID> activeOrgUnitIds = new HashSet<>();
        LocalDate today = LocalDate.now();
        for (StaffAppointment appointment : appointmentRepository.findByUserId(userId)) {
            if (appointment.isActiveOn(today)) {
                activeOrgUnitIds.add(appointment.getOrgUnit().getId());
            }
        }
        if (activeOrgUnitIds.isEmpty()) {
            return false;
        }
        // Walk from the target unit up toward the root: an appointment at any ancestor covers
        // everything beneath it, not only the exact unit named on the appointment.
        OrgUnit current = orgUnitRepository.findById(orgUnitId).orElse(null);
        while (current != null) {
            if (activeOrgUnitIds.contains(current.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR))) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to change organizational structure");
        }
    }
}
