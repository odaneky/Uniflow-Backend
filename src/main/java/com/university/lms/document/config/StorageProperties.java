package com.university.lms.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code endpoint} left unset targets real AWS S3; set it (as the dev/test MinIO service does) to
 * point the same client at a self-hosted, S3-API-compatible store instead.
 */
@ConfigurationProperties("lms.storage")
public record StorageProperties(
        String localRoot,
        long maxUploadBytes,
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey) {}
