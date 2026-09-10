package com.yqz.openblog.article.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.dto.ArticlePublishedContentCachePayload;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.redis.config.RedisProperties;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.redis.core.RedisOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 已发布文章的 Redis 缓存服务。
 * <p>
 * 包含三部分缓存：
 * <ol>
 *   <li>文章正文缓存（个体）：缓存相对稳定的正文与元数据。详情读路径命中缓存后，
 *       计数类字段（阅读/点赞/收藏/评论数）仍实时从数据库合并，不随缓存过期。</li>
 *   <li>文章列表缓存（分页）：使用版本号机制，任何影响列表的写操作会递增版本号，
 *       使旧版本缓存自然过期，避免全量扫描删除。
 *       注意：列表 payload 内含各计数（见 {@code ArticleService.mapListItems}），命中后直接
 *       反序列化、不查库——列表计数最多滞后一个列表 TTL。这是有意取舍：点赞/收藏/评论/阅读
 *       等计数写不触发版本号失效，避免高并发计数把整份列表缓存打爆；可接受的滞后范围
 *       由 {@code article-list-ttl-minutes} 决定。</li>
 *   <li>不存在的文章 id 的负缓存：详情接口遇到不存在的 id 时记录之，使同一 id 的重复请求直接
 *       404、不再查库。只对「不存在」写标记，不对「存在但未发布」写——后者随时可能被发布，
 *       写标记会与发布路径的失效构成竞态。</li>
 * </ol>
 * Redis 不可用或读写异常时：读侧返回 empty，写侧忽略，由 {@link ArticleService} 直接走数据库。
 * <p>
 * fail-open 的代价是失效操作失败时旧缓存不会被清掉，滞后窗口由各自 TTL 决定，且两者不对称：
 * <ul>
 *   <li>{@code evictPublishedList} 静默失败 → 列表的文章集合与排序最长陈旧
 *       {@code openblog.cache.article-list-ttl-minutes}（默认 5）分钟</li>
 *   <li>{@code evict} 静默失败 → 正文/标题最长陈旧 {@code openblog.cache.article-published-ttl-minutes}
 *       （默认 30）分钟</li>
 * </ul>
 * 即「Redis 抖动时管理员改了已发布文章」这一最痛场景的窗口取正文 TTL，调大该值前请先评估这一点。
 */
@Service
public class ArticlePublishedContentCacheService {

    private static final TypeReference<PageResult<ArticleListItemResponse>> LIST_PAGE_TYPE =
            new TypeReference<>() {};

    private static final Logger log = LoggerFactory.getLogger(ArticlePublishedContentCacheService.class);

    private final RedisOps redisOps;
    private final ObjectMapper objectMapper;
    private final RedisProperties redisProperties;

    public ArticlePublishedContentCacheService(
            RedisOps redisOps,
            ObjectMapper objectMapper,
            RedisProperties redisProperties) {
        this.redisOps = redisOps;
        this.objectMapper = objectMapper;
        this.redisProperties = redisProperties;
    }

    // ==================== 文章正文缓存（个体） ====================

    public Optional<ArticlePublishedContentCachePayload> get(Long articleId) {
        if (articleId == null) {
            return Optional.empty();
        }
        String json = redisOps.get(RedisKeys.articleBody(articleId)).orElse(null);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ArticlePublishedContentCachePayload.class));
        } catch (Exception e) {
            log.warn("反序列化文章正文缓存失败。articleId={}", articleId, e);
            return Optional.empty();
        }
    }

    public void put(Long articleId, ArticlePublishedContentCachePayload payload) {
        if (articleId == null || payload == null) {
            return;
        }
        int minutes = Math.max(1, redisProperties.getArticlePublishedTtlMinutes());
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisOps.set(RedisKeys.articleBody(articleId), json, Duration.ofMinutes(minutes));
        } catch (Exception e) {
            log.warn("序列化文章正文缓存失败（已忽略）。articleId={}", articleId, e);
        }
    }

    /**
     * 清除正文缓存与 404 负缓存标记。
     * <p>
     * 负缓存标记只会在「id 不存在」时写入（见 {@link #markMissing(Long)}），而不存在的 id 不会
     * 变为可读，故此处对标记的清除是防御性的：保持 {@code evict} 的语义始终为「彻底丢弃该 id 的
     * 一切缓存」，即便将来放宽写标记的条件也不会残留。
     */
    public void evict(Long articleId) {
        if (articleId == null) {
            return;
        }
        redisOps.delete(RedisKeys.articleBody(articleId));
        redisOps.delete(RedisKeys.articleMissing(articleId));
    }

    // ==================== 文章 404 负缓存 ====================

    /**
     * 该 id 是否已知不可读。命中即可直接返回 404，无需查库。
     * <p>
     * Redis 异常时 {@code hasKey} 返回 false（fail-open），退化为按原逻辑查库。
     */
    public boolean isMissing(Long articleId) {
        if (articleId == null) {
            return false;
        }
        return redisOps.hasKey(RedisKeys.articleMissing(articleId));
    }

    /**
     * 记录「该 id 不存在」，避免扫描器、失效外链、爬虫重试等反复请求同一 id 时每次都查库。
     * <p>
     * 调用方只应在确认行不存在时写入（不存在的 id 不会变为可读，标记不会失效）。代价是详情读路径
     * 多一次 Redis 存在性检查（约 0.2ms），换取 404 路径完全不查库；正缓存命中时的数据库读是计数
     * 实时合并所必需，不受影响。TTL 见 {@code openblog.cache.article-missing-ttl-minutes}。
     */
    public void markMissing(Long articleId) {
        if (articleId == null) {
            return;
        }
        int minutes = Math.max(1, redisProperties.getArticleMissingTtlMinutes());
        redisOps.set(RedisKeys.articleMissing(articleId), "1", Duration.ofMinutes(minutes));
    }

    // ==================== 文章列表缓存（分页） ====================

    /**
     * 读取当前列表版本号。版本号用于构造缓存 key，不存在时默认为 0。
     * <p>
     * 一次「读缓存 → 查库 → 写回缓存」流程中只应调用本方法一次，并把同一个版本号分别传给
     * {@link #getList(long, Long, int, int)} 与 {@link #putList(long, Long, int, int, PageResult)}。
     * 若写回时重新读取版本号，则并发的写操作递增版本号后，基于旧数据算出的结果会被写到新版本的
     * key 上，从而在列表 TTL 内持续返回陈旧的文章集合（漏掉新发布的文章、保留已下线的文章）。
     */
    public long currentVersion() {
        return redisOps.get(RedisKeys.CONTENT_ARTICLE_LIST_VERSION)
                .map(v -> {
                    try {
                        return Long.parseLong(v);
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                })
                .orElse(0L);
    }

    /**
     * 递增列表版本号，使所有旧版本列表缓存自然过期（靠 TTL 清理）。
     * 任何影响已发布列表的写操作（发布、取消发布、更新已发布文章、删除）都应调用此方法。
     * <p>
     * fail-open：Redis 故障时 {@code increment} 静默失败且不阻断写操作，此时列表最多陈旧
     * {@code openblog.cache.article-list-ttl-minutes} 分钟。
     */
    public void evictPublishedList() {
        redisOps.increment(RedisKeys.CONTENT_ARTICLE_LIST_VERSION);
    }

    /**
     * @param version {@link #currentVersion()} 的返回值；必须与写回时的
     *                {@link #putList(long, Long, int, int, PageResult)} 使用同一个版本号
     */
    public Optional<PageResult<ArticleListItemResponse>> getList(long version, Long categoryId, int page, int size) {
        String json = redisOps.get(RedisKeys.articleList(version, categoryId, page, size)).orElse(null);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, LIST_PAGE_TYPE));
        } catch (Exception e) {
            log.warn("反序列化文章列表缓存失败。categoryId={}, page={}, size={}", categoryId, page, size, e);
            return Optional.empty();
        }
    }

    /**
     * @param version 与 {@link #getList(long, Long, int, int)} 使用的同一个版本号，禁止在此重新读取
     */
    public void putList(long version, Long categoryId, int page, int size, PageResult<ArticleListItemResponse> payload) {
        if (payload == null) {
            return;
        }
        int minutes = Math.max(1, redisProperties.getArticleListTtlMinutes());
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisOps.set(RedisKeys.articleList(version, categoryId, page, size), json, Duration.ofMinutes(minutes));
        } catch (Exception e) {
            log.warn("序列化文章列表缓存失败（已忽略）。categoryId={}, page={}, size={}", categoryId, page, size, e);
        }
    }
}
