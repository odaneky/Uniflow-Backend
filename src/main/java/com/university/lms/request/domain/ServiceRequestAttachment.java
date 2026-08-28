package com.university.lms.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * D9: links a student-submitted document to the request it is evidence for. Deliberately a thin
 * join, no verification workflow of its own — unlike {@code admissions.ApplicationDocument},
 * nothing here needs to be checked off before the request can proceed; staff simply see it and
 * decide. Mirrors that entity's composite-key shape.
 */
@Entity
@Table(name = "service_request_attachments")
@IdClass(ServiceRequestAttachment.ServiceRequestAttachmentId.class)
@Getter
public class ServiceRequestAttachment {

    @Id
    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected ServiceRequestAttachment() {
        // for JPA
    }

    public ServiceRequestAttachment(UUID requestId, UUID documentId, UUID uploadedBy, Instant uploadedAt) {
        this.requestId = requestId;
        this.documentId = documentId;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public record ServiceRequestAttachmentId(UUID requestId, UUID documentId) implements Serializable {}
}
