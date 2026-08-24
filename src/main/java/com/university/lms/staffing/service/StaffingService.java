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
import com.university.lms.staffing.domain.OrgUnitType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StaffingService implements StaffAppointments {

    private static final Logger log = LoggerFactory.getLogger(StaffingService.class);

    /** A5 groundwork: the default scope for an appointment nobody has assigned a real unit yet. Seeded by V82. */
    private static final String INSTITUTION_ROOT_CODE = "UNIV";

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

    /**
     * A5 groundwork: gives a staff-role holder a default institution-wide appointment if they do
     * not already have one for this role, so {@link #isAppointedOver} has real data to consult.
     * Called automatically whenever a role is granted (see {@code RoleGrantedAppointmentHandler});
     * not exposed as its own endpoint since nothing outside that automatic path should be creating
     * appointments this way — a real one, at a real unit, is what {@link #appointStaff} is for.
     *
     * <p>No-op for {@code STUDENT} and for a role that already has an open appointment here.
     */
    @Transactional
    public void ensureAppointment(UUID userId, String role) {
        if (userId == null || role == null || SecurityRoles.STUDENT.equals(role)) {
            return;
        }
        OrgUnit root = institutionRoot();
        if (appointmentRepository.existsByUserIdAndOrgUnitIdAndRoleAndValidToIsNull(userId, root.getId(), role)) {
            return;
        }
        appointmentRepository.save(new StaffAppointment(userId, root, role, LocalDate.now()));
        log.info("Appointed user {} to the institution root with role {} (A5 provisioning)", userId, role);
    }

    /**
     * A5 groundwork: backfills a default institution-wide appointment for every current holder of
     * a staff role who lacks one. Idempotent — safe to re-run, e.g. after a role was granted
     * directly at the identity provider, bypassing {@code grantRole} and its automatic appointment.
     *
     * <p>Preserves today's {@code isStaff()} reach exactly: an appointment at the root covers every
     * descendant unit, so this narrows nothing on its own — it only makes narrowing possible later.
     */
    @Transactional
    public int reconcileAppointments() {
        requireRegistry();
        OrgUnit root = institutionRoot();
        int created = 0;
        for (String role : SecurityRoles.STAFF_ROLES) {
            for (UserDirectory.UserSummary user : userDirectory.findByRealmRole(role)) {
                if (!appointmentRepository.existsByUserIdAndOrgUnitIdAndRoleAndValidToIsNull(
                        user.id(), root.getId(), role)) {
                    appointmentRepository.save(new StaffAppointment(user.id(), root, role, LocalDate.now()));
                    created++;
                }
            }
        }
        log.info("Reconciled staff appointments: {} created", created);
        return created;
    }

    /**
     * A5 groundwork: mirrors an academic faculty or department as a real {@link OrgUnit} staff can
     * be appointed to, so an appointment can eventually name "this department" rather than only
     * "the whole institution". Called both when a faculty/department is created (see {@code
     * AcademicOrgUnitHandler}) and by a registry-triggered reconcile pass for existing ones.
     *
     * <p>Namespaces the mirrored code by source type ({@code "FAC:" + code}, {@code "DEPT:" +
     * code}) — {@code org_units.code} is unique institution-wide, but a faculty and a department
     * are free to share a code in their own module, and often will (a "SCI" faculty containing a
     * "SCI" department is a completely ordinary structure). No-op if this source is already linked.
     *
     * <p>Flat under the institution root rather than nested under the faculty's own unit for a
     * department: getting that nesting right depends on the faculty's unit already existing, which
     * asynchronous, independently-ordered outbox delivery cannot guarantee. Flat still gives real
     * department-level appointment granularity — nesting can follow once this proves out.
     */
    @Transactional
    public void ensureOrgUnitFor(String sourceType, UUID sourceId, String code, String name) {
        if (sourceType == null || sourceId == null || code == null) {
            return;
        }
        if (orgUnitRepository.existsBySourceTypeAndSourceId(sourceType, sourceId)) {
            return;
        }
        OrgUnitType unitType;
        String codePrefix;
        if ("FACULTY".equals(sourceType)) {
            unitType = OrgUnitType.FACULTY;
            codePrefix = "FAC";
        } else if ("DEPARTMENT".equals(sourceType)) {
            unitType = OrgUnitType.DEPARTMENT;
            codePrefix = "DEPT";
        } else {
            unitType = OrgUnitType.ADMINISTRATIVE_OFFICE;
            codePrefix = "ADMIN";
        }
        String namespacedCode = codePrefix + ":" + code;
        if (orgUnitRepository.existsByCode(namespacedCode)) {
            log.warn(
                    "Skipped creating an org unit for {} {} — code {} is already in use",
                    sourceType,
                    sourceId,
                    namespacedCode);
            return;
        }
        OrgUnit root = institutionRoot();
        OrgUnit unit = new OrgUnit(root, namespacedCode, name, unitType, sourceType, sourceId);
        orgUnitRepository.saveAndFlush(unit);
        log.info("Linked {} {} to a new org unit {} (A5 provisioning)", sourceType, sourceId, namespacedCode);
    }

    private OrgUnit institutionRoot() {
        return orgUnitRepository
                .findByCode(INSTITUTION_ROOT_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Institution root org unit is missing — migration V82 has not run"));
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

    @Override
    public java.util.Optional<UUID> orgUnitFor(String sourceType, UUID sourceId) {
        if (sourceType == null || sourceId == null) {
            return java.util.Optional.empty();
        }
        return orgUnitRepository.findBySourceTypeAndSourceId(sourceType, sourceId).map(OrgUnit::getId);
    }

    private void requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR))) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to change organizational structure");
        }
    }
}
