package com.yqz.openblog.media.storage;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.media.MediaProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "openblog.storage.type", havingValue = "minio")
public class MinioMediaStorage implements MediaStorage {

    private static final String ORIGINAL_PREFIX = "original/";
    private static final String THUMB_PREFIX = "thumb/";

    private final MinioClient minioClient;
    private final MediaProperties properties;

    public MinioMediaStorage(MinioClient minioClient, MediaProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public String storageType() {
        return "MINIO";
    }

    @Override
    public void saveOriginal(String key, Path file) throws IOException {
        putObject(ORIGINAL_PREFIX + key, file, guessContentType(key));
    }

    @Override
    public void saveThumb(String key, Path file) throws IOException {
        putObject(THUMB_PREFIX + key, file, guessContentType(key));
    }

    @Override
    public byte[] readOriginal(String key) throws IOException {
        return readObject(ORIGINAL_PREFIX + key);
    }

    @Override
    public byte[] readThumb(String key) throws IOException {
        return readObject(THUMB_PREFIX + key);
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket())
                            .object(ORIGINAL_PREFIX + key)
                            .build());
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket())
                            .object(THUMB_PREFIX + key)
                            .build());
        } catch (Exception e) {
            throw new IOException("删除 MinIO 文件失败: " + key, e);
        }
    }

    private void putObject(String objectName, Path file, String contentType) throws IOException {
        ensureBucket();
        try (InputStream in = Files.newInputStream(file)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket())
                            .object(objectName)
                            .stream(in, Files.size(file), -1)
                            .contentType(contentType)
                            .build());
        } catch (Exception e) {
            throw new IOException("上传 MinIO 失败: " + objectName, e);
        }
    }

    private byte[] readObject(String objectName) throws IOException {
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket())
                        .object(objectName)
                        .build())) {
            return in.readAllBytes();
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new BizException(4042, "媒体文件不存在");
            }
            throw new IOException("读取 MinIO 失败: " + objectName, e);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("读取 MinIO 失败: " + objectName, e);
        }
    }

    private void ensureBucket() throws IOException {
        String bucket = bucket();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IOException("检查或创建 MinIO 桶失败: " + bucket, e);
        }
    }

    private String bucket() {
        String bucket = properties.getMinio().getBucket();
        if (!StringUtils.hasText(bucket)) {
            throw new BizException(5001, "未配置 openblog.storage.minio.bucket");
        }
        return bucket;
    }

    private String guessContentType(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "application/octet-stream";
    }
}
