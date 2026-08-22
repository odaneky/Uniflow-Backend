package com.university.lms.course.dto;

import com.university.lms.course.domain.RequirementKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReplaceCourseRequirementsRequest(@NotNull @Valid List<RequirementGroupRequest> groups) {

    public record RequirementGroupRequest(
            @NotNull RequirementKind kind,
            @Min(1) @Max(9) Integer minimumLevel,
            List<UUID> anyOfCourseIds) {}
}
