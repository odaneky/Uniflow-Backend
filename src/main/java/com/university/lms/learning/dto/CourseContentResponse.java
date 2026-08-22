package com.university.lms.learning.dto;

import com.university.lms.learning.domain.CourseContent;
import java.util.List;
import java.util.UUID;

public record CourseContentResponse(
        UUID id, UUID courseSectionId, String overview, List<LearningModuleResponse> modules) {

    public static CourseContentResponse from(CourseContent content, List<LearningModuleResponse> modules) {
        return new CourseContentResponse(content.getId(), content.getCourseSectionId(), content.getOverview(), modules);
    }

    public static CourseContentResponse empty(UUID courseSectionId) {
        return new CourseContentResponse(null, courseSectionId, null, List.of());
    }
}
