package com.codebymike.slidehub.ui.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Configura el cliente AWS S3 v2 (CLAUDE.md §9.5.2).
 *
 * AWS SDK v2 es la única excepción justificada al patrón WebClient-only
 * porque S3 requiere firma SigV4 que el SDK maneja automáticamente.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(
            @Value("${aws.s3.region}") String region,
            @Value("${aws.access-key-id}") String accessKeyId,
            @Value("${aws.secret-access-key}") String secretKey) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretKey)))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${aws.s3.region}") String region,
            @Value("${aws.access-key-id}") String accessKeyId,
            @Value("${aws.secret-access-key}") String secretKey) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretKey)))
                .build();
    }
}
