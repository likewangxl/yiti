package com.bank.branch.platform.governance.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置 — 仅为兼容历史 ExportStrategy 保留 bean 注册。
 * 自由报表/文件管理已切换到本地磁盘存储（见 FileService）。
 * MinioClient 是惰性的，启动时不会真连 endpoint，只在调用 putObject 时才会建立连接。
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

    @Bean
    @ConditionalOnMissingBean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean(name = "minioBucketName")
    @ConditionalOnMissingBean(name = "minioBucketName")
    public String minioBucketName() {
        return bucket;
    }
}
