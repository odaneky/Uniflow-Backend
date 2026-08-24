package com.university.lms.academic.web;

import com.university.lms.academic.dto.CreateFacultyRequest;
import com.university.lms.academic.dto.FacultyResponse;
import com.university.lms.academic.dto.ReconcileOrgUnitsResponse;
import com.university.lms.academic.service.AcademicStructureService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.common.dto.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Faculties — the top of the academic hierarchy. */
@RestController
@RequestMapping("/api/v1/faculties")
public class FacultyController {

    private final AcademicStructureService service;

    public FacultyController(AcademicStructureService service) {
        this.service = service;
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping
    public ResponseEntity<FacultyResponse> create(@Valid @RequestBody CreateFacultyRequest request) {
        FacultyResponse created = service.createFaculty(request);
        return ResponseEntity.created(URI.create("/api/v1/faculties/" + created.id())).body(created);
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping
    public PageResponse<FacultyResponse> findAll(
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.findFaculties(pageable);
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping("/{id}")
    public FacultyResponse findById(@PathVariable UUID id) {
        return service.findFaculty(id);
    }

    /**
     * A5 groundwork: mirrors every existing faculty and department as a real {@code OrgUnit} staff
     * can be appointed to. Idempotent — safe to re-run.
     */
    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/reconcile-org-units")
    public ReconcileOrgUnitsResponse reconcileOrgUnits() {
        return new ReconcileOrgUnitsResponse(service.reconcileOrgUnits());
    }
}
