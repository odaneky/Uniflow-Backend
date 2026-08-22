package com.university.lms.learning.web;

import com.university.lms.learning.dto.CourseContentResponse;
import com.university.lms.learning.service.LearningService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own published course material. The student is never named in the request. */
@RestController
@RequestMapping("/api/v1/me/courses")
public class MyLearningController {

    private final LearningService learningService;

    public MyLearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/{sectionId}/content")
    public CourseContentResponse content(@PathVariable UUID sectionId) {
        return learningService.ownContent(sectionId);
    }
}
