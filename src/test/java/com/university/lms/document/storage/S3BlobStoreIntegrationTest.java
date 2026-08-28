package com.university.lms.document.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.document.config.StorageProperties;
import com.university.lms.document.domain.DocumentStoreException;
import com.university.lms.document.domain.StorageProvider;
import com.university.lms.support.TestObjectStore;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Against a real MinIO, not a fake — the behaviour this class exists for (a second {@link
 * BlobStore} that survives what {@link LocalFilesystemBlobStore} cannot: surviving a restart,
 * being reachable from more than one instance) is exactly what an in-memory substitute would get
 * right by construction regardless of whether the S3 client code is correct.
 *
 * <p>Same escape hatch as {@code AbstractPostgresIntegrationTest} — see {@link TestObjectStore} —
 * for the same reason: a container runtime may be absent, or its bundled Testcontainers client may
 * be unable to negotiate with the daemon at all.
 */
class S3BlobStoreIntegrationTest {

    private static MinIOContainer container;
    private static StorageProperties properties;
    private static S3BlobStore store;

    @BeforeAll
    static void startMinio() {
        if (TestObjectStore.isExternalConfigured()) {
            properties = new StorageProperties(
                    null,
                    0,
                    TestObjectStore.endpoint(),
                    null,
                    "test-bucket-" + UUID.randomUUID(),
                    TestObjectStore.accessKey(),
                    TestObjectStore.secretKey(),
                    "minio");
        } else {
            Assumptions.assumeTrue(
                    dockerAvailable(),
                    "No MinIO available — set -D" + TestObjectStore.ENDPOINT_PROPERTY
                            + " or start a container runtime; S3BlobStore integration test skipped");
            container = new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2024-11-07T00-52-20Z"));
            container.start();
            properties = new StorageProperties(
                    null,
                    0,
                    container.getS3URL(),
                    null,
                    "test-bucket",
                    container.getUserName(),
                    container.getPassword(),
                    "minio");
        }
        store = new S3BlobStore(properties);
    }

    @AfterAll
    static void stopMinio() {
        if (container != null) container.stop();
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Test
    void roundTripsBytesThroughARealBucket() {
        String key = "documents/" + UUID.randomUUID() + "/transcript.pdf";
        byte[] content = "not actually a PDF".getBytes(StandardCharsets.UTF_8);

        store.put(key, content);

        assertThat(store.get(key)).isEqualTo(content);
    }

    @Test
    void reportsItsProviderAsMinio() {
        assertThat(store.provider()).isEqualTo(StorageProvider.MINIO);
    }

    /**
     * Pins a real bug: this bean used to report {@link StorageProvider#MINIO} unconditionally, and
     * {@code lms.storage.provider=s3} did not even activate it — only {@code minio} did, despite
     * the class's own javadoc describing S3 as supported. Same client, same endpoint override (S3
     * setups leave {@code endpoint} unset in production; this test still points it at MinIO purely
     * to reuse the container), only the reported {@link StorageProvider} should differ.
     */
    @Test
    void reportsItsProviderAsS3WhenConfiguredForS3() {
        StorageProperties s3Properties = new StorageProperties(
                properties.localRoot(),
                properties.maxUploadBytes(),
                properties.endpoint(),
                properties.region(),
                properties.bucket(),
                properties.accessKey(),
                properties.secretKey(),
                "s3");
        S3BlobStore s3Store = new S3BlobStore(s3Properties);
        assertThat(s3Store.provider()).isEqualTo(StorageProvider.S3);
    }

    @Test
    void readingAMissingKeyFailsAsAStoreErrorNotSilently() {
        assertThatThrownBy(() -> store.get("documents/does-not-exist"))
                .isInstanceOf(DocumentStoreException.class);
    }

    @Test
    void creatingASecondStoreAgainstTheSameBucketDoesNotFailOnTheAlreadyExistsCase() {
        // The constructor's ensureBucketExists must be idempotent: a second instance (a second
        // application node, or just this test re-running against the same bucket) points at a
        // bucket the first one already created.
        new S3BlobStore(properties);
    }
}
