package com.yqz.openblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用级缓存参数（Redis）。
 */
@ConfigurationProperties(prefix = "openblog.cache")
public class CacheProperties {

    /**
     * 已发布文章正文与元数据在 Redis 中的 TTL（分钟）。Redis 不可用时自动仅走数据库。
     */
    private int articlePublishedTtlMinutes = 30;

    public int getArticlePublishedTtlMinutes() {
        return articlePublishedTtlMinutes;
    }

    public void setArticlePublishedTtlMinutes(int articlePublishedTtlMinutes) {
        this.articlePublishedTtlMinutes = articlePublishedTtlMinutes;
    }
}
