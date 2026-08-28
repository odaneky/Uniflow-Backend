package com.university.lms.document.service;

import com.university.lms.document.domain.Document;
import com.university.lms.document.repository.DocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * F4: purges documents past their {@code expiresAt}. Modelled directly on {@code OutboxDispatcher}
 * — finding candidates and purging each one are deliberately separate transactions (see {@link
 * DocumentPurger}), so one row's failure cannot mark a whole batch's transaction rollback-only and
 * silently discard every document already purged in it.
 */
@Component
public class DocumentRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(DocumentRetentionSweeper.class);

    private final DocumentRepository documentRepository;
    private final DocumentPurger documentPurger;

    @Value("${lms.documents.retention.batch-size:100}")
    private int batchSize;

    public DocumentRetentionSweeper(DocumentRepository documentRepository, DocumentPurger documentPurger) {
        this.documentRepository = documentRepository;
        this.documentPurger = documentPurger;
    }

    @Scheduled(fixedDelayString = "${lms.documents.retention.sweep-interval-ms:3600000}")
    public void sweep() {
        Instant now = Instant.now();
        for (UUID documentId : claimBatch(now)) {
            try {
                documentPurger.purge(documentId);
            } catch (RuntimeException ex) {
                log.error("Could not purge expired document {}: {}", documentId, ex.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    List<UUID> claimBatch(Instant now) {
        return documentRepository.findByExpiresAtBefore(now, PageRequest.of(0, batchSize)).stream()
                .map(Document::getId)
                .toList();
    }

    /** Visible for integration tests. */
    public void drainOnce() {
        sweep();
    }
}
