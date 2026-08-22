package com.university.lms.document.domain;

/**
 * Where the bytes physically live.
 *
 * <p>Recorded per document so the estate can be migrated between providers incrementally, rather
 * than requiring one flag-day cutover for every file the university holds.
 */
public enum StorageProvider {
    S3,
    GCS,
    AZURE_BLOB,
    MINIO,
    LOCAL_FILESYSTEM
}
