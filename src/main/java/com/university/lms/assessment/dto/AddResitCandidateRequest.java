package com.university.lms.assessment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddResitCandidateRequest(@NotNull(message = "is required") UUID studentId) {}
