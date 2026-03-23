package com.yqz.openblog.article.service;

import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.limiter.SlidingWindowLimiter;
import com.yqz.openblog.article.repo.ArticleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 文章阅读次数记录：
 * - 使用 Redis 滑动窗口限流（5 分钟、同一 viewer 最多 1 次有效阅读）
 * - 限流通过后才更新 MySQL 的 view_count
 */
@Service
public class ArticleViewService {

    private static final long WINDOW_MS = 300_000L; // 5 min
    private static final int LIMIT_PER_WINDOW = 1;

    private static final Logger log = LoggerFactory.getLogger(ArticleViewService.class);

    private final SlidingWindowLimiter limiter;
    private final ArticleMapper articleMapper;

    public ArticleViewService(SlidingWindowLimiter limiter, ArticleMapper articleMapper) {
        this.limiter = limiter;
        this.articleMapper = articleMapper;
    }

    /**
     * @return true=已成功增加一次 viewCount
     */
    public boolean tryRecordView(Article article, String viewerKey) {
        if (article == null || article.getId() == null) return false;
        if (article.getStatus() != ArticleStatus.PUBLISHED) return false;
        if (viewerKey == null || viewerKey.isBlank()) return false;

        String redisKey = buildRedisKey(article.getId(), viewerKey);
        long nowMs = System.currentTimeMillis();
        String member = nowMs + "-" + UUID.randomUUID();

        boolean allowed;
        try {
            allowed = limiter.tryAcquire(redisKey, WINDOW_MS, LIMIT_PER_WINDOW, nowMs, member);
        } catch (Exception e) {
            // Redis 宕机/异常时降级：不计数但不影响页面
            log.warn("record view skipped due to redis error. articleId={}, viewerKey={}", article.getId(), viewerKey, e);
            return false;
        }

        if (!allowed) return false;

        try {
            int updated = articleMapper.incrementViewCount(article.getId(), ArticleStatus.PUBLISHED);
            return updated > 0;
        } catch (Exception e) {
            log.warn("record view skipped due to db error. articleId={}, viewerKey={}", article.getId(), viewerKey, e);
            return false;
        }
    }

    private String buildRedisKey(Long articleId, String viewerKey) {
        return "openblog:view:" + articleId + ":" + viewerKey;
    }
}

