package com.university.lms.learning.dto;

import com.university.lms.learning.domain.Lesson;
import java.util.List;
import java.util.UUID;

public record LessonResponse(
        UUID id, String title, String summary, int position, List<LearningMaterialResponse> materials) {

    public static LessonResponse from(Lesson lesson, List<LearningMaterialResponse> materials) {
        return new LessonResponse(
                lesson.getId(), lesson.getTitle(), lesson.getSummary(), lesson.getPosition(), materials);
    }
}
