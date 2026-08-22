package com.university.lms.curriculum.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddRequirementCourseRequest(@NotNull(message = "is required") UUID courseId) {}
