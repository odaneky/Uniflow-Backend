package com.university.lms.curriculum.web;

import com.university.lms.curriculum.dto.AddRequirementCourseRequest;
import com.university.lms.curriculum.dto.CreateRequirementBlockRequest;
import com.university.lms.curriculum.dto.RequirementBlockResponse;
import com.university.lms.curriculum.service.CurriculumService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Requirement blocks attached to a degree programme. */
@RestController
@RequestMapping("/api/v1/programmes/{programmeId}/requirement-blocks")
public class ProgrammeRequirementController {

    private final CurriculumService curriculumService;

    public ProgrammeRequirementController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @GetMapping
    public List<RequirementBlockResponse> list(@PathVariable UUID programmeId) {
        return curriculumService.blocksOf(programmeId);
    }

    @PostMapping
    public ResponseEntity<RequirementBlockResponse> create(
            @PathVariable UUID programmeId, @Valid @RequestBody CreateRequirementBlockRequest request) {
        RequirementBlockResponse created = curriculumService.createBlock(programmeId, request);
        return ResponseEntity.created(URI.create(
                        "/api/v1/programmes/" + programmeId + "/requirement-blocks/" + created.id()))
                .body(created);
    }

    @PostMapping("/{blockId}/courses")
    public RequirementBlockResponse addCourse(
            @PathVariable UUID programmeId,
            @PathVariable UUID blockId,
            @Valid @RequestBody AddRequirementCourseRequest request) {
        return curriculumService.addCourse(programmeId, blockId, request);
    }

    @DeleteMapping("/{blockId}")
    public ResponseEntity<Void> delete(@PathVariable UUID programmeId, @PathVariable UUID blockId) {
        curriculumService.deleteBlock(programmeId, blockId);
        return ResponseEntity.noContent().build();
    }
}
