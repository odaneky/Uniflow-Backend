package com.university.lms.document.service;

import com.university.lms.document.repository.DocumentRepository;
import com.university.lms.document.storage.BlobStore;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes exactly one expired document's blob and row, in its own independent transaction.
 *
 * <p>Split out of {@link DocumentRetentionSweeper} for the same reason {@code OutboxRowProcessor}
 * is split out of {@code OutboxDispatcher}: {@code REQUIRES_NEW} only opens a genuinely new physical
 * transaction when the call arrives through the Spring proxy, and a method calling another method on
 * {@code this} never goes through it. One document already gone (blob deleted, row race-deleted by
 * another instance) must not roll back or block the rest of the sweep's batch.
 */
@Component
class DocumentPurger {

    private final DocumentRepository documentRepository;
    private final BlobStore blobStore;

    DocumentPurger(DocumentRepository documentRepository, BlobStore blobStore) {
        this.documentRepository = documentRepository;
        this.blobStore = blobStore;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void purge(UUID documentId) {
        documentRepository.findById(documentId).ifPresent(document -> {
            // Idempotent — see BlobStore.delete's javadoc — so an already-gone blob is not an error.
            blobStore.delete(document.getStorageKey());
            documentRepository.delete(document);
        });
    }
}
