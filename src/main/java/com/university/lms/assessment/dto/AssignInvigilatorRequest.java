package com.university.lms.assessment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignInvigilatorRequest(@NotNull(message = "is required") UUID userId) {}
