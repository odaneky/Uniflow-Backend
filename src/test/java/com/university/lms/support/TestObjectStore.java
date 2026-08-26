package com.university.lms.support;

/**
 * Resolves which MinIO the {@code S3BlobStore} integration test runs against.
 *
 * <p>Same reasoning and same escape hatch as {@link TestDatabase}: Testcontainers is what CI uses,
 * but where its bundled Docker client cannot negotiate with the daemon at all, an already-running
 * MinIO can be supplied instead — for local dev, exactly the one docker-compose.yml already starts:
 *
 * <pre>{@code
 * ./mvnw test -Dtest=S3BlobStoreIntegrationTest \
 *   -Dlms.test.minio.endpoint=http://localhost:9000 \
 *   -Dlms.test.minio.access-key=lms-minio -Dlms.test.minio.secret-key=lms-minio-secret
 * }</pre>
 */
public final class TestObjectStore {

    public static final String ENDPOINT_PROPERTY = "lms.test.minio.endpoint";
    public static final String ACCESS_KEY_PROPERTY = "lms.test.minio.access-key";
    public static final String SECRET_KEY_PROPERTY = "lms.test.minio.secret-key";

    private static final String ENDPOINT_ENV = "LMS_TEST_MINIO_ENDPOINT";
    private static final String ACCESS_KEY_ENV = "LMS_TEST_MINIO_ACCESS_KEY";
    private static final String SECRET_KEY_ENV = "LMS_TEST_MINIO_SECRET_KEY";

    private TestObjectStore() {}

    /** True when an externally managed MinIO has been supplied. */
    public static boolean isExternalConfigured() {
        String endpoint = endpoint();
        return endpoint != null && !endpoint.isBlank();
    }

    public static String endpoint() {
        return resolve(ENDPOINT_PROPERTY, ENDPOINT_ENV, null);
    }

    public static String accessKey() {
        return resolve(ACCESS_KEY_PROPERTY, ACCESS_KEY_ENV, "lms-minio");
    }

    public static String secretKey() {
        return resolve(SECRET_KEY_PROPERTY, SECRET_KEY_ENV, "lms-minio-secret");
    }

    private static String resolve(String property, String environmentVariable, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
