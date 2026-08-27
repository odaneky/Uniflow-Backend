package com.university.lms.admissions.web;

import com.university.lms.admissions.domain.ApplicationStatus;
import com.university.lms.admissions.dto.ApplicationResponse;
import com.university.lms.admissions.dto.DecideApplicationRequest;
import com.university.lms.admissions.dto.MatriculateApplicationRequest;
import com.university.lms.admissions.dto.ApplicationScoreResponse;
import com.university.lms.admissions.dto.RejectApplicationDocumentRequest;
import com.university.lms.admissions.dto.SubmitApplicationScoreRequest;
import com.university.lms.admissions.dto.TransitionApplicationRequest;
import com.university.lms.admissions.service.AdmissionsService;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
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

    @AccessClass(STAFF_ONLY)
    @GetMapping("/queue")
    public PageResponse<ApplicationResponse> queue(
            @RequestParam(required = false) List<ApplicationStatus> status,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) String reference,
            @PageableDefault(size = 50, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return admissionsService.queue(status, mine, reference, pageable);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/applications/{id}")
    public ApplicationResponse byId(@PathVariable UUID id) {
        return admissionsService.findById(id);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/applications/{id}/claim")
    public ApplicationResponse claim(@PathVariable UUID id) {
        return admissionsService.staffClaim(id);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/applications/{id}/review")
    public ApplicationResponse review(
            @PathVariable UUID id, @Valid @RequestBody(required = false) TransitionApplicationRequest body) {
        return admissionsService.review(id, body);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/applications/{id}/decide")
    public ApplicationResponse decide(@PathVariable UUID id, @Valid @RequestBody DecideApplicationRequest body) {
        return admissionsService.decide(id, body);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/applications/{id}/deposit")
    public ApplicationResponse recordDeposit(@PathVariable UUID id) {
        return admissionsService.recordDeposit(id);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/applications/{id}/matriculate")
    public ApplicationResponse matriculate(
            @PathVariable UUID id, @Valid @RequestBody MatriculateApplicationRequest body) {
        return admissionsService.matriculate(id, body);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/applications/{id}/documents/{documentId}/verify")
    public ApplicationResponse verifyDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        return admissionsService.verifyDocument(id, documentId);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/applications/{id}/documents/{documentId}/reject")
    public ApplicationResponse rejectDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @Valid @RequestBody RejectApplicationDocumentRequest body) {
        return admissionsService.rejectDocument(id, documentId, body.reason());
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/applications/{id}/scores")
    public List<ApplicationScoreResponse> scores(@PathVariable UUID id) {
        return admissionsService.scoresFor(id);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/applications/{id}/scores")
    public List<ApplicationScoreResponse> submitScore(
            @PathVariable UUID id, @Valid @RequestBody SubmitApplicationScoreRequest body) {
        return admissionsService.submitScore(id, body);
    }
}
