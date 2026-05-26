package com.yqz.openblog.article.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.dto.ArticlePublishedContentCachePayload;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.config.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private static final String KEY_PREFIX = "openblog:article:published:content:";
    private static final String LIST_KEY_PREFIX = "openblog:article:published:list:v";
    private static final String LIST_VERSION_KEY = "openblog:article:published:list:version";

    private static final TypeReference<PageResult<ArticleListItemResponse>> LIST_PAGE_TYPE =
            new TypeReference<>() {};

    private static final Logger log = LoggerFactory.getLogger(ArticlePublishedContentCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;

    public ArticlePublishedContentCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    // ==================== 文章正文缓存（个体） ====================

    public Optional<ArticlePublishedContentCachePayload> get(Long articleId) {
        if (articleId == null) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(key(articleId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ArticlePublishedContentCachePayload.class));
        } catch (Exception e) {
            log.warn("读取文章正文缓存失败，降级为数据库。articleId={}", articleId, e);
            return Optional.empty();
        }
    }

    public void put(Long articleId, ArticlePublishedContentCachePayload payload) {
        if (articleId == null || payload == null) {
            return;
        }
        int minutes = Math.max(1, cacheProperties.getArticlePublishedTtlMinutes());
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(key(articleId), json, Duration.ofMinutes(minutes));
        } catch (Exception e) {
            log.warn("写入文章正文缓存失败（已忽略，不影响响应）。articleId={}", articleId, e);
        }
    }

    public void evict(Long articleId) {
        if (articleId == null) {
            return;
        }
        try {
            redisTemplate.delete(key(articleId));
        } catch (Exception e) {
            log.warn("删除文章正文缓存失败（已忽略）。articleId={}", articleId, e);
        }
    }

    // ==================== 文章列表缓存（分页） ====================

    /**
     * 获取当前列表版本号。版本号用于构造缓存 key，不存在时默认为 0。
     */
    private long getListVersion() {
        try {
            String v = redisTemplate.opsForValue().get(LIST_VERSION_KEY);
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            log.warn("读取列表版本号失败，降级为 version=0。", e);
            return 0L;
        }
    }

    /**
     * 递增列表版本号，使所有旧版本列表缓存自然过期（靠 TTL 清理）。
     * 任何影响已发布列表的写操作（发布、取消发布、更新已发布文章、删除）都应调用此方法。
     */
    public void evictPublishedList() {
        try {
            redisTemplate.opsForValue().increment(LIST_VERSION_KEY);
        } catch (Exception e) {
            log.warn("递增列表版本号失败（已忽略）。", e);
        }
    }

    public Optional<PageResult<ArticleListItemResponse>> getList(Long categoryId, int page, int size) {
        try {
            long version = getListVersion();
            String json = redisTemplate.opsForValue().get(listKey(version, categoryId, page, size));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, LIST_PAGE_TYPE));
        } catch (Exception e) {
            log.warn("读取文章列表缓存失败，降级为数据库。categoryId={}, page={}, size={}", categoryId, page, size, e);
            return Optional.empty();
        }
    }

    public void putList(Long categoryId, int page, int size, PageResult<ArticleListItemResponse> payload) {
        if (payload == null) {
            return;
        }
        int minutes = Math.max(1, cacheProperties.getArticleListTtlMinutes());
        try {
            long version = getListVersion();
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(listKey(version, categoryId, page, size), json, Duration.ofMinutes(minutes));
        } catch (Exception e) {
            log.warn("写入文章列表缓存失败（已忽略）。categoryId={}, page={}, size={}", categoryId, page, size, e);
        }
    }

    // ==================== Key 构造 ====================

    private static String key(Long articleId) {
        return KEY_PREFIX + articleId;
    }

    private static String listKey(long version, Long categoryId, int page, int size) {
        return LIST_KEY_PREFIX + version + ":" + (categoryId == null ? "all" : categoryId) + ":" + page + ":" + size;
    }
}
