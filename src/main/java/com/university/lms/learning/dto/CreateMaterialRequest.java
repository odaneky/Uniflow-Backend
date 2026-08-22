package com.university.lms.learning.dto;

import com.university.lms.learning.domain.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Attaches a resource to a lesson. Bytes are never accepted; link or document id only. */
public record CreateMaterialRequest(
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String title,
        @NotNull(message = "is required") MaterialType materialType,
        @Size(max = 1000, message = "must be at most 1000 characters") String externalUrl,
        UUID documentId,
        Integer position) {}
