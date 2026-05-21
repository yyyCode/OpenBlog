package com.yqz.openblog.article.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.article.dto.ArticlePublishedContentCachePayload;
import com.yqz.openblog.config.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 已发布文章「正文 + 相对稳定元数据」的 Redis 缓存（默认 30 分钟）。
 * <p>
 * Redis 不可用或读写异常时：读侧返回 empty，写侧忽略，由 {@link ArticleService} 直接走数据库。
 */
@Service
public class ArticlePublishedContentCacheService {

    private static final String KEY_PREFIX = "openblog:article:published:content:";

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

    private static String key(Long articleId) {
        return KEY_PREFIX + articleId;
    }
}
