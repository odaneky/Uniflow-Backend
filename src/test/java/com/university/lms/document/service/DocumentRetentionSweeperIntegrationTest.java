package com.university.lms.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.document.domain.Document;
import com.university.lms.document.domain.DocumentStoreException;
import com.university.lms.document.domain.DocumentType;
import com.university.lms.document.repository.DocumentRepository;
import com.university.lms.document.storage.BlobStore;
import com.university.lms.identity.domain.User;
import com.university.lms.identity.repository.UserRepository;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Against a real database and a real (local-filesystem) {@link BlobStore} — proves an expired
 * document's bytes are actually gone, not just its metadata row, and that a non-expired or
 * never-expiring document is left untouched by the same sweep.
 */
class DocumentRetentionSweeperIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private BlobStore blobStore;

    @Autowired
    private DocumentRetentionSweeper sweeper;

    @Autowired
    private UserRepository userRepository;

    private UUID newOwnerId() {
        String subject = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(User.fromIdentityProvider(
                subject, "owner-" + subject.substring(0, 8), subject.substring(0, 8) + "@university.test",
                "Owner", "Test"));
        return user.getId();
    }

    @Test
    @DisplayName("an expired document's blob and row are both purged")
    void purgesAnExpiredDocumentsBlobAndRow() {
        String key = "uploads/" + UUID.randomUUID() + "/expired.pdf";
        blobStore.put(key, "expired content".getBytes());
        Document expired = new Document(
                DocumentType.IDENTIFICATION, "id.pdf", "application/pdf", 10, key, blobStore.provider(), newOwnerId());
        expired.scheduleExpiry(Instant.now().minus(1, ChronoUnit.DAYS));
        Document saved = documentRepository.saveAndFlush(expired);

        sweeper.drainOnce();

        assertThat(documentRepository.findById(saved.getId())).isEmpty();
        assertThatThrownBy(() -> blobStore.get(key)).isInstanceOf(DocumentStoreException.class);
    }

    @Test
    @DisplayName("a document not yet expired is left alone")
    void doesNotPurgeANonExpiredDocument() {
        String key = "uploads/" + UUID.randomUUID() + "/current.pdf";
        blobStore.put(key, "current content".getBytes());
        Document current = new Document(
                DocumentType.IDENTIFICATION, "id.pdf", "application/pdf", 10, key, blobStore.provider(), newOwnerId());
        current.scheduleExpiry(Instant.now().plus(365, ChronoUnit.DAYS));
        Document saved = documentRepository.saveAndFlush(current);

        sweeper.drainOnce();

        assertThat(documentRepository.findById(saved.getId())).isPresent();
        assertThat(blobStore.get(key)).isNotNull();
    }

    @Test
    @DisplayName("a document with no expiry (a transcript, say) is never purged")
    void neverPurgesADocumentWithNoExpiry() {
        String key = "uploads/" + UUID.randomUUID() + "/transcript.pdf";
        blobStore.put(key, "transcript".getBytes());
        Document neverExpires = new Document(
                DocumentType.TRANSCRIPT, "t.pdf", "application/pdf", 10, key, blobStore.provider(), newOwnerId());
        Document saved = documentRepository.saveAndFlush(neverExpires);

        sweeper.drainOnce();

        assertThat(documentRepository.findById(saved.getId())).isPresent();
    }
}
