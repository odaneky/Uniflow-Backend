package com.university.lms.document.dto;

import com.university.lms.document.domain.DocumentType;
import com.university.lms.document.domain.StorageProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Registers metadata for a file that already lives in object storage. Never accepts bytes. */
public record CreateDocumentRequest(
        @NotNull(message = "is required") UUID ownerUserId,
        @NotNull(message = "is required") DocumentType documentType,
        @NotBlank(message = "is required") @Size(max = 255, message = "must be at most 255 characters") String fileName,
        @NotBlank(message = "is required") @Size(max = 150, message = "must be at most 150 characters")
                String contentType,
        @NotNull(message = "is required") @PositiveOrZero(message = "must be at least 0") Long sizeBytes,
        @NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters")
                String storageKey,
        @NotNull(message = "is required") StorageProvider storageProvider) {}
