package com.university.lms.document.dto;

import com.university.lms.document.domain.Document;
import com.university.lms.document.domain.DocumentType;
import java.time.Instant;
import java.util.UUID;

/** Metadata a client may see. The storage key is an internal pointer and is never returned. */
public record DocumentResponse(
        UUID id,
        DocumentType documentType,
        String fileName,
        String contentType,
        long sizeBytes,
        Instant createdAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getDocumentType(),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getCreatedAt());
    }
}
