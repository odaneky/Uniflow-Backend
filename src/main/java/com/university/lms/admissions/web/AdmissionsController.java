package com.university.lms.admissions.web;

import com.university.lms.admissions.domain.ApplicationStatus;
import com.university.lms.admissions.dto.ApplicationResponse;
import com.university.lms.admissions.dto.DecideApplicationRequest;
import com.university.lms.admissions.dto.MatriculateApplicationRequest;
import com.university.lms.admissions.dto.TransitionApplicationRequest;
import com.university.lms.admissions.service.AdmissionsService;
import com.university.lms.common.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Staff admissions queue and workflow transitions. */
@RestController
@RequestMapping("/api/v1/admissions")
public class AdmissionsController {

    private final AdmissionsService admissionsService;

    public AdmissionsController(AdmissionsService admissionsService) {
        this.admissionsService = admissionsService;
    }

    @GetMapping("/queue")
    public PageResponse<ApplicationResponse> queue(
            @RequestParam(required = false) List<ApplicationStatus> status,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) String reference,
            @PageableDefault(size = 50, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return admissionsService.queue(status, mine, reference, pageable);
    }

    @GetMapping("/applications/{id}")
    public ApplicationResponse byId(@PathVariable UUID id) {
        return admissionsService.findById(id);
    }

    @PostMapping("/applications/{id}/claim")
    public ApplicationResponse claim(@PathVariable UUID id) {
        return admissionsService.staffClaim(id);
    }

    @PostMapping("/applications/{id}/review")
    public ApplicationResponse review(
            @PathVariable UUID id, @Valid @RequestBody(required = false) TransitionApplicationRequest body) {
        return admissionsService.review(id, body);
    }

    @PostMapping("/applications/{id}/decide")
    public ApplicationResponse decide(@PathVariable UUID id, @Valid @RequestBody DecideApplicationRequest body) {
        return admissionsService.decide(id, body);
    }

    @PostMapping("/applications/{id}/deposit")
    public ApplicationResponse recordDeposit(@PathVariable UUID id) {
        return admissionsService.recordDeposit(id);
    }

    @PostMapping("/applications/{id}/matriculate")
    public ApplicationResponse matriculate(
            @PathVariable UUID id, @Valid @RequestBody MatriculateApplicationRequest body) {
        return admissionsService.matriculate(id, body);
    }
}
