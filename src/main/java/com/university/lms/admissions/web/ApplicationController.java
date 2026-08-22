package com.university.lms.admissions.web;

import com.university.lms.admissions.dto.ApplicationResponse;
import com.university.lms.admissions.dto.AttachApplicationDocumentRequest;
import com.university.lms.admissions.dto.CreateApplicationRequest;
import com.university.lms.admissions.dto.UpdateApplicationRequest;
import com.university.lms.admissions.service.AdmissionsService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public applicant endpoints for creating and tracking applications. */
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final AdmissionsService admissionsService;

    public ApplicationController(AdmissionsService admissionsService) {
        this.admissionsService = admissionsService;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        ApplicationResponse created = admissionsService.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/applications/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ApplicationResponse byId(@PathVariable UUID id) {
        return admissionsService.findById(id);
    }

    @PatchMapping("/{id}")
    public ApplicationResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateApplicationRequest request) {
        return admissionsService.update(id, request);
    }

    @PostMapping("/{id}/submit")
    public ApplicationResponse submit(@PathVariable UUID id) {
        return admissionsService.submit(id);
    }

    @PostMapping("/{id}/documents")
    public ApplicationResponse attachDocument(
            @PathVariable UUID id, @Valid @RequestBody AttachApplicationDocumentRequest request) {
        return admissionsService.attachDocument(id, request);
    }
}
