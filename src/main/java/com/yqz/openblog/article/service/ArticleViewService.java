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
 * 文章阅读次数（按客户端 IP 去重）：
 * Redis 滑动窗口（5 分钟、同一 IP 对同一文章最多 1 次有效阅读），通过后累加 articles.view_count。
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
     * @param clientIp 客户端 IP（由 ClientIpResolver 解析）
     * @return true 已成功增加一次 viewCount
     */
    public boolean tryRecordView(Article article, String clientIp) {
        if (article == null || article.getId() == null) return false;
        if (article.getStatus() != ArticleStatus.PUBLISHED) return false;
        if (clientIp == null || clientIp.isBlank()) return false;

        String redisKey = buildRedisKey(article.getId(), clientIp);
        long nowMs = System.currentTimeMillis();
        String member = nowMs + "-" + UUID.randomUUID();

        boolean allowed;
        try {
            allowed = limiter.tryAcquire(redisKey, WINDOW_MS, LIMIT_PER_WINDOW, nowMs, member);
        } catch (Exception e) {
            // Redis 不可用时降级为直接写库，否则阅读量永远不涨；此模式下不做 IP 去重（刷新会多次 +1）
            log.warn("Redis 不可用，文章阅读量将直接写入数据库（无滑动窗口去重）。articleId={}, clientIp={}",
                    article.getId(), clientIp, e);
            allowed = true;
        }

        if (!allowed) return false;

        try {
            int updated = articleMapper.incrementViewCount(article.getId(), ArticleStatus.PUBLISHED);
            return updated > 0;
        } catch (Exception e) {
            log.warn("record view skipped due to db error. articleId={}, clientIp={}", article.getId(), clientIp, e);
            return false;
        }
    }

    private String buildRedisKey(Long articleId, String clientIp) {
        return "openblog:article:view:" + articleId + ":" + clientIp;
    }
}

