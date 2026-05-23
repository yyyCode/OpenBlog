package com.yqz.openblog.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openblog.storage")
public class MediaProperties {

    /**
     * 存储类型：local（本地磁盘）或 minio（对象存储）。
     */
    private String type = "local";

    private String rootPath;
    private String publicBaseUrl;
    private int thumbLongEdge;
    private Minio minio = new Minio();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isMinioEnabled() {
        return "minio".equalsIgnoreCase(type);
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public int getThumbLongEdge() {
        return thumbLongEdge;
    }

    public void setThumbLongEdge(int thumbLongEdge) {
        this.thumbLongEdge = thumbLongEdge;
    }

    public Minio getMinio() {
        return minio;
    }

    public void setMinio(Minio minio) {
        this.minio = minio;
    }

    public static class Minio {
        /**
         * MinIO 服务地址，例如 http://127.0.0.1:9000
         */
        private String endpoint;
        private String accessKey;
        private String secretKey;
        /**
         * 存储桶名称，不存在时应用启动上传阶段会自动创建。
         */
        private String bucket;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }
}
