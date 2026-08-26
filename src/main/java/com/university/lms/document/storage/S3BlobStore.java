package com.university.lms.document.storage;

import com.university.lms.document.config.StorageProperties;
import com.university.lms.document.domain.DocumentStoreException;
import com.university.lms.document.domain.StorageProvider;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Blob storage against any S3-API-compatible endpoint: self-hosted MinIO in dev/test (this is the
 * bean this class registers as, {@link StorageProvider#MINIO}), or real AWS S3 in production by
 * leaving {@code lms.storage.endpoint} unset and pointing {@code lms.storage.provider} at a bean
 * registered for {@link StorageProvider#S3} instead — same client, same {@code storageKey} scheme,
 * a different endpoint and credentials source is the only change either needs.
 */
@Component
@ConditionalOnProperty(name = "lms.storage.provider", havingValue = "minio")
public class S3BlobStore implements BlobStore {

    private final S3Client client;
    private final String bucket;

    S3BlobStore(StorageProperties properties) {
        this.bucket = require(properties.bucket(), "lms.storage.bucket");
        S3ClientBuilder builder =
                S3Client.builder().region(regionOf(properties)).httpClient(UrlConnectionHttpClient.create());
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            // MinIO: force path-style bucket addressing and point at the self-hosted endpoint
            // instead of resolving *.amazonaws.com.
            builder = builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        if (properties.accessKey() != null && !properties.accessKey().isBlank()) {
            builder = builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));
        }
        this.client = builder.build();
        ensureBucketExists();
    }

    @Override
    public void put(String storageKey, byte[] content) {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(storageKey).build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException ex) {
            throw new DocumentStoreException("Could not write object " + storageKey, ex);
        }
    }

    @Override
    public byte[] get(String storageKey) {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(storageKey).build())
                    .asByteArray();
        } catch (SdkException ex) {
            throw new DocumentStoreException("Could not read object " + storageKey, ex);
        }
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.MINIO;
    }

    // MinIO does not pre-create buckets; a fresh dev/test instance needs one on first use. Real S3
    // buckets are provisioned out of band (Terraform/console), so this is idempotent either way —
    // it's a no-op once the bucket already exists. headBucket's 404 doesn't reliably surface as the
    // typed NoSuchBucketException across S3-API implementations, so check the status code instead.
    private void ensureBucketExists() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw ex;
            }
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private static Region regionOf(StorageProperties properties) {
        return Region.of(properties.region() == null || properties.region().isBlank() ? "us-east-1" : properties.region());
    }

    private static String require(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be set when lms.storage.provider=minio");
        }
        return value;
    }
}
