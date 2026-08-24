package com.university.lms.academic.web;

import com.university.lms.academic.dto.CreateDepartmentRequest;
import com.university.lms.academic.dto.DepartmentResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Departments. A course belongs to one of these, which is why this endpoint gates course creation. */
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final AcademicStructureService service;

    public DepartmentController(AcademicStructureService service) {
        this.service = service;
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse created = service.createDepartment(request);
        return ResponseEntity.created(URI.create("/api/v1/departments/" + created.id())).body(created);
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping
    public PageResponse<DepartmentResponse> findAll(
            @RequestParam(required = false) UUID facultyId,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.findDepartments(facultyId, pageable);
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping("/{id}")
    public DepartmentResponse findById(@PathVariable UUID id) {
        return service.findDepartment(id);
    }
}
