package com.cypher.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/** AWS SDK clients use the EC2 instance role in production, never static keys. */
@Configuration
public class S3StorageConfig {

    @Bean
    public S3Client s3Client(
            @Value("${cypher.s3.region:ap-south-1}") String region,
            @Value("${cypher.s3.endpoint:}") String endpoint,
            @Value("${cypher.s3.path-style:false}") boolean pathStyle,
            @Value("${cypher.s3.access-key:}") String accessKey,
            @Value("${cypher.s3.secret-key:}") String secretKey) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());
        applyStaticCredentials(builder, accessKey, secretKey);
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint.trim()));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${cypher.s3.region:ap-south-1}") String region,
            @Value("${cypher.s3.public-endpoint:}") String publicEndpoint,
            @Value("${cypher.s3.path-style:false}") boolean pathStyle,
            @Value("${cypher.s3.access-key:}") String accessKey,
            @Value("${cypher.s3.secret-key:}") String secretKey) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());
        applyStaticCredentials(builder, accessKey, secretKey);
        if (publicEndpoint != null && !publicEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(publicEndpoint.trim()));
        }
        return builder.build();
    }

    private static void applyStaticCredentials(
            S3ClientBuilder builder, String accessKey, String secretKey) {
        if (hasText(accessKey) && hasText(secretKey)) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())));
        }
    }

    private static void applyStaticCredentials(
            S3Presigner.Builder builder, String accessKey, String secretKey) {
        if (hasText(accessKey) && hasText(secretKey)) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
