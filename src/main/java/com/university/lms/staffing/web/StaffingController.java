package com.university.lms.staffing.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.staffing.dto.AppointStaffRequest;
import com.university.lms.staffing.dto.CreateOrgUnitRequest;
import com.university.lms.staffing.dto.EmployeeResponse;
import com.university.lms.staffing.dto.OrgUnitResponse;
import com.university.lms.staffing.dto.ReconcileAppointmentsResponse;
import com.university.lms.staffing.dto.RegisterEmployeeRequest;
import com.university.lms.staffing.dto.StaffAppointmentResponse;
import com.university.lms.staffing.service.StaffingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Organizational structure and staff appointments — the A4 foundation org-scoped authorization builds on. */
@RestController
public class StaffingController {

    private final StaffingService staffingService;

    public StaffingController(StaffingService staffingService) {
        this.staffingService = staffingService;
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/api/v1/org-units")
    public ResponseEntity<OrgUnitResponse> createOrgUnit(@Valid @RequestBody CreateOrgUnitRequest request) {
        OrgUnitResponse created = staffingService.createOrgUnit(request);
        return ResponseEntity.created(URI.create("/api/v1/org-units/" + created.id())).body(created);
    }

    @AccessClass(REGISTRY_ONLY)
    @GetMapping("/api/v1/org-units/{id}/children")
    public List<OrgUnitResponse> childrenOf(@PathVariable UUID id) {
        return staffingService.childrenOf(id);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/api/v1/employees")
    public ResponseEntity<EmployeeResponse> registerEmployee(@Valid @RequestBody RegisterEmployeeRequest request) {
        EmployeeResponse created = staffingService.registerEmployee(request);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + created.id())).body(created);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/api/v1/staff-appointments")
    public ResponseEntity<StaffAppointmentResponse> appointStaff(@Valid @RequestBody AppointStaffRequest request) {
        StaffAppointmentResponse created = staffingService.appointStaff(request);
        return ResponseEntity.created(URI.create("/api/v1/staff-appointments/" + created.id())).body(created);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/api/v1/staff-appointments/{id}/end")
    public ResponseEntity<Void> endAppointment(@PathVariable UUID id, @RequestParam LocalDate validTo) {
        staffingService.endAppointment(id, validTo);
        return ResponseEntity.noContent().build();
    }

    @AccessClass(REGISTRY_ONLY)
    @GetMapping("/api/v1/staff-appointments")
    public List<StaffAppointmentResponse> appointmentsOf(@RequestParam UUID userId) {
        return staffingService.appointmentsOf(userId);
    }

    /**
     * A5 groundwork: backfills a default institution-wide appointment for every current holder of
     * a staff role who lacks one. Idempotent — safe to re-run.
     */
    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/api/v1/staff-appointments/reconcile")
    public ReconcileAppointmentsResponse reconcile() {
        return new ReconcileAppointmentsResponse(staffingService.reconcileAppointments());
    }
}
