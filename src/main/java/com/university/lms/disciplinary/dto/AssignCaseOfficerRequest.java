package com.university.lms.disciplinary.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignCaseOfficerRequest(@NotNull(message = "is required") UUID officerUserId) {}
