package com.university.lms.course.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RolloverTermRequest(
        @NotNull(message = "is required") UUID targetTermId, boolean dryRun) {}
