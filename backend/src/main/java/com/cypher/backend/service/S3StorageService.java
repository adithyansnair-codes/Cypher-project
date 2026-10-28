package com.cypher.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
public class S3StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final String prefix;

    public S3StorageService(
            S3Client s3,
            S3Presigner presigner,
            @Value("${cypher.s3.bucket:}") String bucket,
            @Value("${cypher.s3.key-prefix:cypher/incidents}") String prefix) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket == null ? "" : bucket.trim();
        this.prefix = prefix == null ? "cypher/incidents" : prefix.replaceAll("^/+|/+$", "");
    }

    public boolean isEnabled() {
        return !bucket.isBlank();
    }

    public String upload(String key, byte[] bytes, String contentType) {
        requireEnabled();
        String fullKey = objectKey(key);
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(fullKey)
                        .contentType(contentType)
                        .serverSideEncryption("AES256")
                        .build(),
                RequestBody.fromBytes(bytes)
        );
        return "s3://" + bucket + "/" + fullKey;
    }

    public String downloadUrl(String storedPath) {
        requireEnabled();
        String key = storedPath.startsWith("s3://")
                ? storedPath.substring(storedPath.indexOf('/', "s3://".length()) + 1)
                : storedPath;
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .getObjectRequest(get)
                        .build()
        ).url().toString();
    }

    public void delete(String storedPath) {
        requireEnabled();
        String key = storedPath.startsWith("s3://")
                ? storedPath.substring(storedPath.indexOf('/', "s3://".length()) + 1)
                : storedPath;
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private String objectKey(String key) {
        return prefix.isBlank() ? key : prefix + "/" + key;
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "S3 storage is not configured. Set S3_BUCKET before uploading media.");
        }
    }
}
