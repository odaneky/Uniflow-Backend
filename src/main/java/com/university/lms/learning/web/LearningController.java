package com.university.lms.learning.web;

import com.university.lms.learning.dto.CourseContentResponse;
import com.university.lms.learning.dto.CreateLessonRequest;
import com.university.lms.learning.dto.CreateMaterialRequest;
import com.university.lms.learning.dto.CreateModuleRequest;
import com.university.lms.learning.dto.LearningMaterialResponse;
import com.university.lms.learning.dto.LearningModuleResponse;
import com.university.lms.learning.dto.LessonResponse;
import com.university.lms.learning.dto.UpsertContentRequest;
import com.university.lms.learning.service.LearningService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff management of a section's teaching material. */
@RestController
@RequestMapping("/api/v1/learning")
public class LearningController {

    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/sections/{sectionId}")
    public CourseContentResponse staffContent(@PathVariable UUID sectionId) {
        return learningService.staffContent(sectionId);
    }

    @PutMapping("/sections/{sectionId}/content")
    public CourseContentResponse upsert(@PathVariable UUID sectionId, @Valid @RequestBody UpsertContentRequest request) {
        return learningService.upsertContent(sectionId, request);
    }

    @PostMapping("/sections/{sectionId}/modules")
    public ResponseEntity<LearningModuleResponse> addModule(
            @PathVariable UUID sectionId, @Valid @RequestBody CreateModuleRequest request) {
        LearningModuleResponse created = learningService.addModule(sectionId, request);
        return ResponseEntity.created(URI.create("/api/v1/learning/modules/" + created.id())).body(created);
    }

    @PostMapping("/modules/{moduleId}/publish")
    public LearningModuleResponse publish(@PathVariable UUID moduleId) {
        return learningService.publishModule(moduleId);
    }

    @PostMapping("/modules/{moduleId}/lessons")
    public ResponseEntity<LessonResponse> addLesson(
            @PathVariable UUID moduleId, @Valid @RequestBody CreateLessonRequest request) {
        LessonResponse created = learningService.addLesson(moduleId, request);
        return ResponseEntity.created(URI.create("/api/v1/learning/lessons/" + created.id())).body(created);
    }

    @PostMapping("/lessons/{lessonId}/materials")
    public ResponseEntity<LearningMaterialResponse> addMaterial(
            @PathVariable UUID lessonId, @Valid @RequestBody CreateMaterialRequest request) {
        LearningMaterialResponse created = learningService.addMaterial(lessonId, request);
        return ResponseEntity.created(URI.create("/api/v1/learning/materials/" + created.id())).body(created);
    }
}
