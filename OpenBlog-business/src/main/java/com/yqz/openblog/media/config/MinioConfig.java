package com.yqz.openblog.media.config;

import com.yqz.openblog.media.MediaProperties;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(name = "openblog.storage.type", havingValue = "minio")
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MediaProperties properties) {
        MediaProperties.Minio minio = properties.getMinio();
        if (!StringUtils.hasText(minio.getEndpoint())) {
            throw new IllegalStateException("启用 MinIO 时必须配置 openblog.storage.minio.endpoint");
        }
        if (!StringUtils.hasText(minio.getAccessKey())) {
            throw new IllegalStateException("启用 MinIO 时必须配置 openblog.storage.minio.access-key");
        }
        if (!StringUtils.hasText(minio.getSecretKey())) {
            throw new IllegalStateException("启用 MinIO 时必须配置 openblog.storage.minio.secret-key");
        }
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }
}
