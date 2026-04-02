package com.bank.branch.platform.governance.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置
 * <p>
 * 基于 application.yml 中的 minio.* 配置项初始化 MinioClient Bean。
 * 提供 minioBucketName Bean 供注入使用。
 * </p>
 */
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    String endpoint;

    @Value("${minio.access-key:minioadmin}")
    String accessKey;

    @Value("${minio.secret-key:minioadmin}")
    String secretKey;

    @Value("${minio.bucket:branch-platform}")
    String bucket;

    /**
     * 创建 MinioClient 实例
     *
     * @return MinioClient
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 提供存储桶名称 Bean
     *
     * @return 存储桶名称
     */
    @Bean
    public String minioBucketName() {
        return bucket;
    }
}
