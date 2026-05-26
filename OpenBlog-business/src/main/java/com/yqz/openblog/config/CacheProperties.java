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

    /**
     * 已发布文章列表页在 Redis 中的 TTL（分钟）。列表页变更频率高，TTL 较短以保证新鲜度。
     */
    private int articleListTtlMinutes = 5;

    public int getArticlePublishedTtlMinutes() {
        return articlePublishedTtlMinutes;
    }

    public void setArticlePublishedTtlMinutes(int articlePublishedTtlMinutes) {
        this.articlePublishedTtlMinutes = articlePublishedTtlMinutes;
    }

    public int getArticleListTtlMinutes() {
        return articleListTtlMinutes;
    }

    public void setArticleListTtlMinutes(int articleListTtlMinutes) {
        this.articleListTtlMinutes = articleListTtlMinutes;
    }
}
