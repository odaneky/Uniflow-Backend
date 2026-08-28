package com.university.lms.financialaid.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.financialaid.dto.CreateScholarshipProgrammeRequest;
import com.university.lms.financialaid.dto.ScholarshipProgrammeResponse;
import com.university.lms.financialaid.dto.UpdateScholarshipProgrammeRequest;
import com.university.lms.financialaid.service.ScholarshipProgrammeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** E9: the catalog of named scholarship funds. */
@RestController
@RequestMapping("/api/v1/scholarship-programmes")
public class ScholarshipProgrammeController {

    private final ScholarshipProgrammeService scholarshipProgrammeService;

    public ScholarshipProgrammeController(ScholarshipProgrammeService scholarshipProgrammeService) {
        this.scholarshipProgrammeService = scholarshipProgrammeService;
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping
    public List<ScholarshipProgrammeResponse> findAll() {
        return scholarshipProgrammeService.findAll();
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping
    public ResponseEntity<ScholarshipProgrammeResponse> create(
            @Valid @RequestBody CreateScholarshipProgrammeRequest request) {
        ScholarshipProgrammeResponse created = scholarshipProgrammeService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/scholarship-programmes/" + created.id())).body(created);
    }

    @AccessClass(REGISTRY_ONLY)
    @PatchMapping("/{programmeId}")
    public ScholarshipProgrammeResponse update(
            @PathVariable UUID programmeId, @Valid @RequestBody UpdateScholarshipProgrammeRequest request) {
        return scholarshipProgrammeService.update(programmeId, request);
    }

    @AccessClass(REGISTRY_ONLY)
    @DeleteMapping("/{programmeId}")
    public void deactivate(@PathVariable UUID programmeId) {
        scholarshipProgrammeService.deactivate(programmeId);
    }
}
