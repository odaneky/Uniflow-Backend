package com.university.lms.admissions.dto;

import com.university.lms.admissions.domain.ApplicationDocument;
import com.university.lms.admissions.domain.DocumentVerificationStatus;
import java.time.Instant;
import java.util.UUID;

public record ApplicationDocumentResponse(
        UUID documentId,
        DocumentVerificationStatus status,
        UUID verifiedBy,
        String verifiedByName,
        Instant verifiedAt,
        String rejectionReason) {

    public static ApplicationDocumentResponse from(ApplicationDocument document, String verifiedByName) {
        return new ApplicationDocumentResponse(
                document.getDocumentId(),
                document.getStatus(),
                document.getVerifiedBy(),
                verifiedByName,
                document.getVerifiedAt(),
                document.getRejectionReason());
    }
}
