package com.yqz.openblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openblog.cache")
public class OpenblogCacheProperties {

    /**
     * 首页首屏相关缓存（文章列表首条、文章详情快照）的 TTL，默认 30 分钟。
     */
    private long homeTtlSeconds = 1800L;

    public long getHomeTtlSeconds() {
        return homeTtlSeconds;
    }

    public void setHomeTtlSeconds(long homeTtlSeconds) {
        this.homeTtlSeconds = homeTtlSeconds;
    }
}
