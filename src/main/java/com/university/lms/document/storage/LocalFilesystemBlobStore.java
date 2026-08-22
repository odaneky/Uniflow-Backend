package com.university.lms.document.storage;

import com.university.lms.document.config.StorageProperties;
import com.university.lms.document.domain.DocumentStoreException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Writes object bytes to a local directory. Production deployments swap this for S3/MinIO without
 * changing {@code documents.storage_key}.
 */
@Component
public class LocalFilesystemBlobStore {

    private final Path root;

    LocalFilesystemBlobStore(StorageProperties properties) {
        this.root = Path.of(properties.localRoot() == null ? "./data/blobs" : properties.localRoot())
                .toAbsolutePath()
                .normalize();
    }

    public void put(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new DocumentStoreException("Could not write object " + storageKey, ex);
        }
    }

    public byte[] get(String storageKey) {
        Path target = resolve(storageKey);
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new DocumentStoreException("Could not read object " + storageKey, ex);
        }
    }

    private Path resolve(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new DocumentStoreException("Refusing a storage key outside the blob root");
        }
        return target;
    }
}
