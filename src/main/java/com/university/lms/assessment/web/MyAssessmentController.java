package com.university.lms.assessment.web;

import com.university.lms.assessment.dto.AssessmentResponse;
import com.university.lms.assessment.service.AssessmentService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Published assessments for a section the caller is taking. */
@RestController
@RequestMapping("/api/v1/me/courses")
public class MyAssessmentController {

    private final AssessmentService assessmentService;

    public MyAssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/{sectionId}/assessments")
    public List<AssessmentResponse> assessments(@PathVariable UUID sectionId) {
        return assessmentService.own(sectionId);
    }
}
