package com.university.lms.document.storage;

import com.university.lms.document.config.StorageProperties;
import com.university.lms.document.domain.DocumentStoreException;
import com.university.lms.document.domain.StorageProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Writes object bytes to a local directory — the default {@link BlobStore}, and the only one this
 * codebase implements today. A remote provider (S3, MinIO, GCS, Azure Blob — {@link StorageProvider}
 * already enumerates the candidates) is real follow-on work, not yet built: it needs a provider
 * choice, a credentials strategy and a bucket/container naming scheme decided first, none of which
 * this class should decide unilaterally. What this class guarantees is that adding one later is a
 * new {@code BlobStore} implementation selected by configuration, not a rewrite of
 * {@code DocumentService}.
 */
@Component
@ConditionalOnProperty(name = "lms.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFilesystemBlobStore implements BlobStore {

    private final Path root;

    LocalFilesystemBlobStore(StorageProperties properties) {
        this.root = Path.of(properties.localRoot() == null ? "./data/blobs" : properties.localRoot())
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public void put(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new DocumentStoreException("Could not write object " + storageKey, ex);
        }
    }

    @Override
    public byte[] get(String storageKey) {
        Path target = resolve(storageKey);
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new DocumentStoreException("Could not read object " + storageKey, ex);
        }
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.LOCAL_FILESYSTEM;
    }

    private Path resolve(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new DocumentStoreException("Refusing a storage key outside the blob root");
        }
        return target;
    }
}
