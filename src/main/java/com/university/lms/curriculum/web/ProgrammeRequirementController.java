package com.university.lms.curriculum.web;

import com.university.lms.curriculum.dto.AddRequirementCourseRequest;
import com.university.lms.curriculum.dto.CreateRequirementBlockRequest;
import com.university.lms.curriculum.dto.RequirementBlockResponse;
import com.university.lms.curriculum.service.CurriculumService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
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

    @AccessClass(AUTHENTICATED)
    @GetMapping
    public List<RequirementBlockResponse> list(@PathVariable UUID programmeId) {
        return curriculumService.blocksOf(programmeId);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping
    public ResponseEntity<RequirementBlockResponse> create(
            @PathVariable UUID programmeId, @Valid @RequestBody CreateRequirementBlockRequest request) {
        RequirementBlockResponse created = curriculumService.createBlock(programmeId, request);
        return ResponseEntity.created(URI.create(
                        "/api/v1/programmes/" + programmeId + "/requirement-blocks/" + created.id()))
                .body(created);
    }

    @PostMapping("/{blockId}/courses")
    @AccessClass(STAFF_ONLY)
    public RequirementBlockResponse addCourse(
            @PathVariable UUID programmeId,
            @PathVariable UUID blockId,
            @Valid @RequestBody AddRequirementCourseRequest request) {
        return curriculumService.addCourse(programmeId, blockId, request);
    }

    @AccessClass(STAFF_ONLY)
    @DeleteMapping("/{blockId}")
    public ResponseEntity<Void> delete(@PathVariable UUID programmeId, @PathVariable UUID blockId) {
        curriculumService.deleteBlock(programmeId, blockId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Publishes the programme's current draft curriculum version. From this moment its requirement
     * blocks are immutable, which is why this is registry-only rather than the general curriculum
     * editor scope the other endpoints here use.
     */
    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/publish")
    public ResponseEntity<Void> publish(@PathVariable UUID programmeId) {
        curriculumService.publishVersion(programmeId);
        return ResponseEntity.noContent().build();
    }
}
