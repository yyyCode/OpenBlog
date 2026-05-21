package com.yqz.openblog.feedback.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.config.ClientIpResolver;
import com.yqz.openblog.feedback.dto.FeedbackCreateRequest;
import com.yqz.openblog.feedback.dto.FeedbackListItemResponse;
import com.yqz.openblog.feedback.entity.FeedbackEntry;
import com.yqz.openblog.feedback.repo.FeedbackRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yqz.openblog.common.PageResult;

import java.time.*;

@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private static final String KEY_PREFIX = "openblog:feedback:ipday:";

    private final FeedbackRepository feedbackRepository;
    private final StringRedisTemplate redisTemplate;

    public FeedbackService(FeedbackRepository feedbackRepository, StringRedisTemplate redisTemplate) {
        this.feedbackRepository = feedbackRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void create(FeedbackCreateRequest req, HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        String ipKey = ClientIpResolver.toRedisKeySegment(ip);

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        // 先用 Redis 进行“每天一次”快速判定；Redis 不可用时降级用 DB 判定
        if (!tryAcquireRedis(ipKey, today, zone)) {
            throw new BizException(4290, "同一 IP 每天只能提交一次");
        }

        // DB 兜底：避免 Redis 不可用/穿透/并发导致多写
        if (feedbackRepository.existsByIpKeyAndSubmitDay(ipKey, today)) {
            throw new BizException(4290, "同一 IP 每天只能提交一次");
        }

        FeedbackEntry e = new FeedbackEntry();
        e.setIpKey(ipKey);
        e.setSubmitDay(today);
        e.setSubmitterName(req.getSubmitterName().trim());
        e.setContent(req.getContent().trim());
        try {
            feedbackRepository.save(e);
        } catch (DataIntegrityViolationException dup) {
            // 并发下唯一键冲突
            throw new BizException(4290, "同一 IP 每天只能提交一次");
        }
    }

    public PageResult<FeedbackListItemResponse> listPending(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Page<FeedbackEntry> pg = feedbackRepository.findAllByStatusOrderByCreatedAtDesc(
                FeedbackEntry.Status.PENDING,
                PageRequest.of(p, s)
        );
        var items = pg.getContent().stream().map(this::toListItem).toList();
        return new PageResult<>(items, pg.getNumber(), pg.getSize(), pg.getTotalElements());
    }

    private FeedbackListItemResponse toListItem(FeedbackEntry e) {
        FeedbackListItemResponse r = new FeedbackListItemResponse();
        r.setId(e.getId());
        r.setSubmitterName(e.getSubmitterName());
        r.setContent(e.getContent());
        r.setSubmitDay(e.getSubmitDay());
        r.setStatus(e.getStatus());
        r.setCreatedAt(e.getCreatedAt());
        return r;
    }

    private boolean tryAcquireRedis(String ipKey, LocalDate day, ZoneId zone) {
        String key = KEY_PREFIX + ipKey + ":" + day;
        try {
            Instant now = Instant.now();
            Instant nextDayStart = day.plusDays(1).atStartOfDay(zone).toInstant();
            Duration ttl = Duration.between(now, nextDayStart);
            if (ttl.isNegative() || ttl.isZero()) {
                ttl = Duration.ofHours(24);
            }
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return ok == null || ok;
        } catch (Exception e) {
            log.warn("feedback rate limit skipped (redis unavailable), ipKey={}", ipKey, e);
            return true;
        }
    }
}

