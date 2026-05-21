package com.yqz.openblog.article.service;

import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.limiter.SlidingWindowLimiter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yqz.openblog.article.repo.ArticleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文章阅读次数（按客户端 IP 去重）：
 * <ul>
 *   <li>优先：Redis 滑动窗口（5 分钟、同一 IP 对同一文章最多 1 次有效阅读）</li>
 *   <li>Redis 不可用时：进程内 Caffeine 同样 5 分钟去重（多实例部署需 Redis 才一致）</li>
 * </ul>
 * 通过后累加 articles.view_count。
 */
@Service
public class ArticleViewService {

    private static final long WINDOW_MS = 300_000L; // 5 min
    private static final int LIMIT_PER_WINDOW = 1;

    private static final Logger log = LoggerFactory.getLogger(ArticleViewService.class);

    /** Redis 故障时的单机去重，键与 Redis 侧一致 */
    private final Cache<String, Boolean> redisDownDedup = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(500_000)
            .build();

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
            log.warn("Redis 不可用，文章阅读量改用进程内 5 分钟去重。articleId={}, clientIp={}",
                    article.getId(), clientIp, e);
            Boolean prev = redisDownDedup.asMap().putIfAbsent(redisKey, Boolean.TRUE);
            allowed = prev == null;
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

