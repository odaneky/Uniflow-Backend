package com.university.lms.assessment.web;

import com.university.lms.assessment.dto.AssessmentResponse;
import com.university.lms.assessment.dto.AttemptResponse;
import com.university.lms.assessment.dto.CreateAssessmentRequest;
import com.university.lms.assessment.service.AssessmentService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff management of assessed work. */
@RestController
@RequestMapping("/api/v1/assessments")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/sections/{sectionId}")
    public List<AssessmentResponse> forSection(@PathVariable UUID sectionId) {
        return assessmentService.forSection(sectionId);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/sections/{sectionId}")
    public ResponseEntity<AssessmentResponse> create(
            @PathVariable UUID sectionId, @Valid @RequestBody CreateAssessmentRequest request) {
        AssessmentResponse created = assessmentService.create(sectionId, request);
        return ResponseEntity.created(URI.create("/api/v1/assessments/" + created.id())).body(created);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/{id}/publish")
    public AssessmentResponse publish(@PathVariable UUID id) {
        return assessmentService.publish(id);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/{id}/attempts")
    public List<AttemptResponse> attempts(@PathVariable UUID id) {
        return assessmentService.attemptsFor(id);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/{id}/attempts/{attemptId}/file")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID attemptId) {
        return MyAttemptController.file(assessmentService.downloadForStaff(id, attemptId));
    }
}
