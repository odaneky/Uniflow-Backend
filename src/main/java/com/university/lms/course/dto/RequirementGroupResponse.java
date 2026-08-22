package com.university.lms.course.dto;

import com.university.lms.course.domain.RequirementKind;
import java.util.List;
import java.util.UUID;

public record RequirementGroupResponse(
        UUID id, RequirementKind kind, Integer minimumLevel, List<RequirementOptionResponse> anyOf) {

    public record RequirementOptionResponse(UUID courseId, String courseCode, String title) {}
}
