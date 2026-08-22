package com.university.lms.admissions.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AttachApplicationDocumentRequest(@NotNull(message = "is required") UUID documentId) {}
