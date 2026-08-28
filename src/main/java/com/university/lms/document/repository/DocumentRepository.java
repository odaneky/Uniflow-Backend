package com.university.lms.document.repository;

import com.university.lms.document.domain.Document;
import com.university.lms.document.domain.DocumentType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the document module. */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByOwnerUserId(UUID ownerUserId, Pageable pageable);

    Page<Document> findByDocumentType(DocumentType documentType, Pageable pageable);

    Optional<Document> findByStorageKey(String storageKey);

    /** F4: candidates for the retention sweeper. {@code expiresAt} is {@code null} for "never". */
    List<Document> findByExpiresAtBefore(Instant instant, Pageable pageable);
}
