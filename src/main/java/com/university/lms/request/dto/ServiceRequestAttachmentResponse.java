package com.university.lms.request.dto;

import java.time.Instant;
import java.util.UUID;

public record ServiceRequestAttachmentResponse(
        UUID documentId,
        String fileName,
        String contentType,
        long sizeBytes,
        UUID uploadedBy,
        String uploadedByName,
        Instant uploadedAt) {}
