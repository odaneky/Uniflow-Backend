package com.university.lms.document.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Metadata for a stored file. <b>Never the file itself.</b>
 *
 * <p>{@code storageKey} is an opaque pointer into an object store. Putting the bytes in PostgreSQL
 * would bloat the row store, make every backup enormous, and drag multi-megabyte payloads through
 * the connection pool that the rest of the system depends on for latency. The database records
 * that a file exists and who may see it; the object store holds the content.
 *
 * <p>{@code checksum} is retained so an upload can be verified and duplicates recognised without
 * re-reading the object.
 */
@Entity
@Table(
        name = "documents",
        indexes = {
            @Index(name = "idx_documents_owner", columnList = "owner_user_id"),
            @Index(name = "idx_documents_type", columnList = "document_type")
        })
@Getter
public class Document extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Opaque key within the provider's namespace; not a URL and never rendered to a client. */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 30)
    private StorageProvider storageProvider;

    /** Cross-module reference into identity. */
    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    /** F4: when retention says this document's bytes should be purged. {@code null} = never. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** F4: honest default is {@link VirusScanStatus#NOT_SCANNED}, not a faked clean bill. */
    @Enumerated(EnumType.STRING)
    @Column(name = "virus_scan_status", nullable = false, length = 20)
    private VirusScanStatus virusScanStatus = VirusScanStatus.NOT_SCANNED;

    protected Document() {
        // for JPA
    }

    public Document(
            DocumentType documentType,
            String fileName,
            String contentType,
            long sizeBytes,
            String storageKey,
            StorageProvider storageProvider,
            UUID ownerUserId) {
        this.documentType = documentType;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.storageProvider = storageProvider;
        this.ownerUserId = ownerUserId;
    }

    public void recordChecksum(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public void scheduleExpiry(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void recordScanResult(VirusScanStatus virusScanStatus) {
        this.virusScanStatus = virusScanStatus;
    }
}
