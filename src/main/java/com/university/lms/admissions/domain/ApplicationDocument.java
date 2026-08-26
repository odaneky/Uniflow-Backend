package com.university.lms.admissions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Links an uploaded document to an application. */
@Entity
@Table(name = "application_documents")
@IdClass(ApplicationDocument.ApplicationDocumentId.class)
@Getter
public class ApplicationDocument {

    @Id
    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    /** G5: whether admissions staff have checked this document — set once, by someone other than the applicant. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentVerificationStatus status = DocumentVerificationStatus.PENDING;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    protected ApplicationDocument() {
        // for JPA
    }

    public ApplicationDocument(UUID applicationId, UUID documentId) {
        this.applicationId = applicationId;
        this.documentId = documentId;
    }

    public void verify(UUID verifiedBy) {
        requirePending();
        this.status = DocumentVerificationStatus.VERIFIED;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = Instant.now();
    }

    public void reject(UUID verifiedBy, String reason) {
        requirePending();
        this.status = DocumentVerificationStatus.REJECTED;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = Instant.now();
        this.rejectionReason = reason;
    }

    private void requirePending() {
        if (status != DocumentVerificationStatus.PENDING) {
            throw new IllegalStateException("This document was already " + status.name().toLowerCase());
        }
    }

    public record ApplicationDocumentId(UUID applicationId, UUID documentId) implements Serializable {}
}
