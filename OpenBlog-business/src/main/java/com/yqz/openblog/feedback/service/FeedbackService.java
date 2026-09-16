package com.yqz.openblog.feedback.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.config.ClientIpResolver;
import com.yqz.openblog.feedback.dto.FeedbackCreateRequest;
import com.yqz.openblog.feedback.dto.FeedbackListItemResponse;
import com.yqz.openblog.feedback.entity.FeedbackEntry;
import com.yqz.openblog.feedback.repo.FeedbackRepository;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.redis.core.RedisOps;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yqz.openblog.common.PageResult;

import java.time.*;

@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    /** 反馈提交人必须是有效账号；JWT 在到期前一直有效，禁用/封禁只查 token 挡不住 */
    private static final String ACTIVE = "ACTIVE";

    /** 同一账号每天一次 */
    private static final int CODE_DUPLICATE = 4290;
    /** 账号不存在 / 非 ACTIVE */
    private static final int CODE_ACCOUNT_UNUSABLE = 4030;

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final RedisOps redisOps;

    public FeedbackService(FeedbackRepository feedbackRepository,
                           UserRepository userRepository,
                           RedisOps redisOps) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.redisOps = redisOps;
    }

    @Transactional
    public void create(FeedbackCreateRequest req, Long userId, HttpServletRequest request) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !ACTIVE.equals(user.getStatus())) {
            log.warn("反馈提交被拒：账号不可用。userId={}", userId);
            throw new BizException(CODE_ACCOUNT_UNUSABLE, "账号状态不可用，无法提交反馈");
        }

        String ipKey = ClientIpResolver.toRedisKeySegment(ClientIpResolver.resolve(request));
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        String key = RedisKeys.feedbackUserDay(userId, today);

        // Redis 只是"今天已提交"的加速判定：命中即拒。Redis 故障时 hasKey 返回 false（fail-open），
        // 继续走 DB 判定，绝不因缓存不可用而误拒正常提交。
        if (redisOps.hasKey(key)) {
            log.info("反馈限流触发。userId={}, day={}", userId, today);
            throw new BizException(CODE_DUPLICATE, "同一账号每天只能提交一次");
        }

        // DB 是权威判据：Redis 被穿透/清空/不可用时仍能挡住重复提交
        if (feedbackRepository.existsByUserIdAndSubmitDay(userId, today)) {
            throw new BizException(CODE_DUPLICATE, "同一账号每天只能提交一次");
        }

        FeedbackEntry e = new FeedbackEntry();
        e.setUserId(userId);
        // 提交人姓名取自账号，不信任请求体（前端不再传）
        e.setSubmitterName(user.getUsername());
        e.setIpKey(ipKey);
        e.setSubmitDay(today);
        e.setContent(req.getContent().trim());
        try {
            feedbackRepository.save(e);
        } catch (DataIntegrityViolationException dup) {
            // 并发下唯一键冲突
            throw new BizException(CODE_DUPLICATE, "同一账号每天只能提交一次");
        }

        // 落库成功后再预热缓存：失败也不影响本次提交（DB 已持久化）
        redisOps.set(key, "1", ttlUntilNextDay(today, zone));
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
        r.setUserId(e.getUserId());
        r.setSubmitterName(e.getSubmitterName());
        r.setContent(e.getContent());
        r.setSubmitDay(e.getSubmitDay());
        r.setStatus(e.getStatus());
        r.setCreatedAt(e.getCreatedAt());
        return r;
    }

    /** 缓存到次日零点失效；跨零点调用时兜底 24 小时 */
    private Duration ttlUntilNextDay(LocalDate day, ZoneId zone) {
        Duration ttl = Duration.between(Instant.now(), day.plusDays(1).atStartOfDay(zone).toInstant());
        return (ttl.isNegative() || ttl.isZero()) ? Duration.ofHours(24) : ttl;
    }
}
