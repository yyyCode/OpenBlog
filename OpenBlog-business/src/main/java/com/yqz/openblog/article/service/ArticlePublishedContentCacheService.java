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
 * 包含两部分缓存：
 * <ol>
 *   <li>文章正文缓存（个体）：缓存相对稳定的正文与元数据，计数类字段每次从数据库合并。</li>
 *   <li>文章列表缓存（分页）：使用版本号机制，任何影响列表的写操作会递增版本号，
 *       使旧版本缓存自然过期，避免全量扫描删除。</li>
 * </ol>
 * Redis 不可用或读写异常时：读侧返回 empty，写侧忽略，由 {@link ArticleService} 直接走数据库。
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

    public void evict(Long articleId) {
        if (articleId == null) {
            return;
        }
        redisOps.delete(RedisKeys.articleBody(articleId));
    }

    // ==================== 文章列表缓存（分页） ====================

    /**
     * 获取当前列表版本号。版本号用于构造缓存 key，不存在时默认为 0。
     */
    private long getListVersion() {
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
     */
    public void evictPublishedList() {
        redisOps.increment(RedisKeys.CONTENT_ARTICLE_LIST_VERSION);
    }

    public Optional<PageResult<ArticleListItemResponse>> getList(Long categoryId, int page, int size) {
        long version = getListVersion();
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

    public void putList(Long categoryId, int page, int size, PageResult<ArticleListItemResponse> payload) {
        if (payload == null) {
            return;
        }
        int minutes = Math.max(1, redisProperties.getArticleListTtlMinutes());
        try {
            long version = getListVersion();
            String json = objectMapper.writeValueAsString(payload);
            redisOps.set(RedisKeys.articleList(version, categoryId, page, size), json, Duration.ofMinutes(minutes));
        } catch (Exception e) {
            log.warn("序列化文章列表缓存失败（已忽略）。categoryId={}, page={}, size={}", categoryId, page, size, e);
        }
    }
}
