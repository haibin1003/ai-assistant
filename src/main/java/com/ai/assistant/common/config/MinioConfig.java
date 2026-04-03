package com.ai.assistant.common.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置
 */
@Slf4j
@Configuration
public class MinioConfig {

    @Value("${ai-assistant.minio.endpoint:http://114.66.38.81:9000}")
    private String endpoint;

    @Value("${ai-assistant.minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${ai-assistant.minio.secret-key:minioadmin123}")
    private String secretKey;

    @Value("${ai-assistant.minio.bucket:ai-assistant}")
    private String bucket;

    @Value("${ai-assistant.minio.enabled:false}")
    private boolean enabled;

    @Bean
    public MinioClient minioClient() {
        // Always try to create MinioClient - each service decides whether to use it
        // based on their own enabled flag (skill.minio.enabled or document.minio.enabled)
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            // 检查 bucket 是否存在，不存在则创建
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            } else {
                log.info("MinIO bucket ready: {}", bucket);
            }

            return client;
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client: {}", e.getMessage(), e);
            log.warn("MinIO not available, will use local file storage");
            return null;
        }
    }

    @Bean
    public String minioBucket() {
        return bucket;
    }

    @Bean
    public boolean minioEnabled() {
        return enabled;
    }
}