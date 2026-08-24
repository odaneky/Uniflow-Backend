package com.university.lms.academic.web;

import com.university.lms.academic.dto.CreateProgrammeRequest;
import com.university.lms.academic.dto.ProgrammeResponse;
import com.university.lms.academic.dto.ReplaceProgrammeCreditLoadRequest;
import com.university.lms.academic.dto.UpdateProgrammeRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Degree programmes. A student is admitted to one of these. */
@RestController
@RequestMapping("/api/v1/programmes")
public class ProgrammeController {

    private final AcademicStructureService service;

    public ProgrammeController(AcademicStructureService service) {
        this.service = service;
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping
    public ResponseEntity<ProgrammeResponse> create(@Valid @RequestBody CreateProgrammeRequest request) {
        ProgrammeResponse created = service.createProgramme(request);
        return ResponseEntity.created(URI.create("/api/v1/programmes/" + created.id())).body(created);
    }

    @AccessClass(PUBLIC)
    @GetMapping
    public PageResponse<ProgrammeResponse> findAll(
            @RequestParam(required = false) UUID departmentId,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.findProgrammes(departmentId, pageable);
    }

    @AccessClass(PUBLIC)
    @GetMapping("/{id}")
    public ProgrammeResponse findById(@PathVariable UUID id) {
        return service.findProgramme(id);
    }

    @AccessClass(STAFF_ONLY)
    @PatchMapping("/{id}")
    public ProgrammeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProgrammeRequest request) {
        return service.updateProgramme(id, request);
    }

    @AccessClass(STAFF_ONLY)
    @PutMapping("/{id}/credit-load")
    public ProgrammeResponse replaceCreditLoad(
            @PathVariable UUID id, @Valid @RequestBody ReplaceProgrammeCreditLoadRequest request) {
        return service.replaceCreditLoad(id, request);
    }
}
