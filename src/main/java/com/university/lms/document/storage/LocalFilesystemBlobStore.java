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
 * Writes object bytes to a local directory — the default {@link BlobStore}, and the only one that
 * keeps a pod's disk as its store. {@link S3BlobStore} is the real remote provider: set {@code
 * lms.storage.provider=minio} (self-hosted MinIO, dev/test) or {@code s3} (real AWS) to register it
 * instead. What this class demonstrates is that the choice is a {@code BlobStore} implementation
 * selected by configuration, not a rewrite of {@code DocumentService}.
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
