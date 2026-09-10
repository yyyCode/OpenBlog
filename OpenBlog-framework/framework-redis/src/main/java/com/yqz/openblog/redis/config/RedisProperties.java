package com.yqz.openblog.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 缓存相关配置参数。
 */
@ConfigurationProperties(prefix = "openblog.cache")
public class RedisProperties {

    /**
     * 已发布文章正文与元数据在 Redis 中的 TTL（分钟）。
     * <p>
     * 该值同时是「Redis 故障导致 {@code evict} 静默失败」时正文/标题的陈旧窗口上界
     * （列表侧对应 {@link #articleListTtlMinutes}），调大前需评估。
     */
    private int articlePublishedTtlMinutes = 30;

    /**
     * 不可读文章（详情接口 404）的负缓存 TTL（分钟）。
     * <p>
     * 文章 id 自增不复用，正常不存在不会「以后出现」，故该值不承担正确性职责，主要作用是控制
     * key 数量（扫描器可产生大量唯一 id）并兜底手工插库等绕过写路径的变更。保持短 TTL（默认 1）。
     */
    private int articleMissingTtlMinutes = 1;

    /**
     * 已发布文章列表页在 Redis 中的 TTL（分钟）。
     * <p>
     * 该值同时是列表缓存两条兜底路径的上界，调大前需评估：
     * <ul>
     *   <li>版本号自增失效（{@code evictPublishedList}）在 Redis 故障时静默失败；</li>
     *   <li>版本号 key 无 TTL，若被 maxmemory 策略驱逐则版本号归零，可能读到残留的旧版本缓存。</li>
     * </ul>
     * 因此不建议显著调大（默认 5 分钟）。
     */
    private int articleListTtlMinutes = 5;

    public int getArticlePublishedTtlMinutes() {
        return articlePublishedTtlMinutes;
    }

    public void setArticlePublishedTtlMinutes(int articlePublishedTtlMinutes) {
        this.articlePublishedTtlMinutes = articlePublishedTtlMinutes;
    }

    public int getArticleMissingTtlMinutes() {
        return articleMissingTtlMinutes;
    }

    public void setArticleMissingTtlMinutes(int articleMissingTtlMinutes) {
        this.articleMissingTtlMinutes = articleMissingTtlMinutes;
    }

    public int getArticleListTtlMinutes() {
        return articleListTtlMinutes;
    }

    public void setArticleListTtlMinutes(int articleListTtlMinutes) {
        this.articleListTtlMinutes = articleListTtlMinutes;
    }
}
