package com.university.lms.document.api;

import java.util.Optional;
import java.util.UUID;

/**
 * The document module's published contract for storing and reading file bytes.
 *
 * <p>Callers receive metadata and content. They never see a storage key: that pointer is internal
 * to this module so the object store can move without rewriting assessment or learning.
 */
public interface DocumentStore {

    record StoredFile(
            UUID id,
            UUID ownerUserId,
            String documentType,
            String fileName,
            String contentType,
            long sizeBytes) {}

    StoredFile store(
            UUID ownerUserId, String documentType, String fileName, String contentType, byte[] content);

    Optional<StoredFile> find(UUID documentId);

    Optional<byte[]> content(UUID documentId);
}
