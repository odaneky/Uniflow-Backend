package com.university.lms.course.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignLecturerRequest(@NotNull(message = "is required") UUID lecturerUserId) {}
