package com.university.lms.document.storage;

import com.university.lms.document.domain.StorageProvider;

/**
 * Where object bytes actually live. Internal to the {@code document} module — other modules never
 * see a storage key or a provider, only {@code document.api.DocumentStore}'s metadata-and-content
 * contract.
 *
 * <p>{@code LocalFilesystemBlobStore}'s own javadoc has long claimed "production deployments swap
 * this for S3/MinIO without changing {@code documents.storage_key}" — true of the storage key
 * scheme, but until this interface existed nothing made the swap itself possible:
 * {@code DocumentService} held a direct field of the concrete local-filesystem class, so a second
 * implementation would have needed {@code DocumentService} rewritten to add it, not configured to
 * select it. This is the seam that claim was describing.
 *
 * <p>Deliberately just two methods. Anything richer — multipart upload, pre-signed URLs for direct
 * browser upload/download, lifecycle policies — is a real design decision for whichever remote
 * provider gets chosen, not something to speculatively add ahead of that choice.
 */
public interface BlobStore {

    void put(String storageKey, byte[] content);

    byte[] get(String storageKey);

    /**
     * F4: retention purge is the first real caller — a document past its {@code expiresAt} needs
     * its bytes actually gone, not just its metadata row. Idempotent: deleting an already-gone key
     * is not an error, the same way {@code S3BlobStore.ensureBucketExists} treats "already there"
     * as success rather than failure.
     */
    void delete(String storageKey);

    /** Which {@link StorageProvider} this instance is, so callers stop hard-coding one. */
    StorageProvider provider();
}
