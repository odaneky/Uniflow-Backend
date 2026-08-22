package com.university.lms.academic.web;

import com.university.lms.academic.dto.CreateFacultyRequest;
import com.university.lms.academic.dto.FacultyResponse;
import com.university.lms.academic.service.AcademicStructureService;
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

    @PostMapping
    public ResponseEntity<FacultyResponse> create(@Valid @RequestBody CreateFacultyRequest request) {
        FacultyResponse created = service.createFaculty(request);
        return ResponseEntity.created(URI.create("/api/v1/faculties/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<FacultyResponse> findAll(
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.findFaculties(pageable);
    }

    @GetMapping("/{id}")
    public FacultyResponse findById(@PathVariable UUID id) {
        return service.findFaculty(id);
    }
}
