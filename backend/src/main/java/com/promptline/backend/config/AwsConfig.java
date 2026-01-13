package com.promptline.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsConfig {

    @Bean
    public S3Client s3Client() {
        // Uses default credentials chain:
        // - EC2 instance role in prod
        // - ~/.aws/credentials in dev
        return S3Client.builder()
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-2")))
                .build();
    }
}
