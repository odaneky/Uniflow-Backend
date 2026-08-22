package com.university.lms.admissions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
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

    protected ApplicationDocument() {
        // for JPA
    }

    public ApplicationDocument(UUID applicationId, UUID documentId) {
        this.applicationId = applicationId;
        this.documentId = documentId;
    }

    public record ApplicationDocumentId(UUID applicationId, UUID documentId) implements Serializable {}
}
