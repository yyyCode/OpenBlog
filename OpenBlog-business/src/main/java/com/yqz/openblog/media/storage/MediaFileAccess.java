package com.yqz.openblog.media.storage;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.media.entity.Media;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MediaFileAccess {

    private final LocalMediaStorage localMediaStorage;
    private final ObjectProvider<MinioMediaStorage> minioMediaStorage;

    public MediaFileAccess(LocalMediaStorage localMediaStorage,
                           ObjectProvider<MinioMediaStorage> minioMediaStorage) {
        this.localMediaStorage = localMediaStorage;
        this.minioMediaStorage = minioMediaStorage;
    }

    public byte[] read(Media media, boolean thumb) throws IOException {
        MediaStorage storage = resolve(media.getStorageType());
        return thumb ? storage.readThumb(media.getStorageKey()) : storage.readOriginal(media.getStorageKey());
    }

    private MediaStorage resolve(String storageType) {
        if ("MINIO".equalsIgnoreCase(storageType)) {
            MinioMediaStorage minio = minioMediaStorage.getIfAvailable();
            if (minio == null) {
                throw new BizException(5001, "当前未启用 MinIO，无法读取 MINIO 类型媒体");
            }
            return minio;
        }
        return localMediaStorage;
    }
}
