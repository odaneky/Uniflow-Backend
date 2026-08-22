package com.university.lms.learning.dto;

import com.university.lms.learning.domain.LearningModule;
import java.util.List;
import java.util.UUID;

public record LearningModuleResponse(
        UUID id, String title, int position, boolean published, List<LessonResponse> lessons) {

    public static LearningModuleResponse from(LearningModule module, List<LessonResponse> lessons) {
        return new LearningModuleResponse(
                module.getId(), module.getTitle(), module.getPosition(), module.isPublished(), lessons);
    }
}
