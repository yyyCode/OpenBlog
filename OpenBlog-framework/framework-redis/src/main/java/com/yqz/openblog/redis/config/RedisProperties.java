package com.yqz.openblog.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 缓存相关配置参数。
 */
@ConfigurationProperties(prefix = "openblog.cache")
public class RedisProperties {

    /**
     * 已发布文章正文与元数据在 Redis 中的 TTL（分钟）。
     */
    private int articlePublishedTtlMinutes = 30;

    /**
     * 已发布文章列表页在 Redis 中的 TTL（分钟）。
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
