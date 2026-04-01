package com.yqz.openblog.site;

import com.yqz.openblog.article.limiter.SlidingWindowLimiter;
import com.yqz.openblog.site.repo.SiteVisitCounterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 全站访问次数（按客户端 IP 去重）：
 * Redis 滑动窗口（5 分钟、同一 IP 最多记 1 次有效访问），通过后累加 MySQL。
 */
@Service
public class SiteVisitService {

    private static final long WINDOW_MS = 300_000L;
    private static final int LIMIT_PER_WINDOW = 1;

    private static final Logger log = LoggerFactory.getLogger(SiteVisitService.class);

    private final SlidingWindowLimiter limiter;
    private final SiteVisitCounterMapper siteVisitCounterMapper;

    public SiteVisitService(SlidingWindowLimiter limiter, SiteVisitCounterMapper siteVisitCounterMapper) {
        this.limiter = limiter;
        this.siteVisitCounterMapper = siteVisitCounterMapper;
    }

    /**
     * @return true 已成功增加一次全站访问计数
     */
    public boolean tryRecordVisit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        String redisKey = "openblog:site:view:" + clientIp;
        long nowMs = System.currentTimeMillis();
        String member = nowMs + "-" + UUID.randomUUID();

        boolean allowed;
        try {
            allowed = limiter.tryAcquire(redisKey, WINDOW_MS, LIMIT_PER_WINDOW, nowMs, member);
        } catch (Exception e) {
            log.warn("Redis 不可用，全站访问将直接写入数据库（无滑动窗口去重）。ip={}", clientIp, e);
            allowed = true;
        }
        if (!allowed) {
            return false;
        }
        try {
            int updated = siteVisitCounterMapper.incrementVisitCount();
            return updated > 0;
        } catch (Exception e) {
            log.warn("record site visit skipped due to db error. ip={}", clientIp, e);
            return false;
        }
    }
}
