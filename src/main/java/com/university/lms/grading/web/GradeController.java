package com.university.lms.grading.web;

import com.university.lms.grading.dto.CreateGradeRequest;
import com.university.lms.grading.dto.GradeResponse;
import com.university.lms.grading.service.GradeService;
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

/** Staff awarding and section gradebook. */
@RestController
@RequestMapping("/api/v1/grades")
public class GradeController {

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping
    public ResponseEntity<GradeResponse> award(@Valid @RequestBody CreateGradeRequest request) {
        GradeResponse created = gradeService.award(request);
        return ResponseEntity.created(URI.create("/api/v1/grades/" + created.id())).body(created);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/{id}/publish")
    public GradeResponse publish(@PathVariable UUID id) {
        return gradeService.publish(id);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/sections/{sectionId}")
    public List<GradeResponse> gradebook(@PathVariable UUID sectionId) {
        return gradeService.gradebook(sectionId);
    }
}
