package com.yqz.openblog.user.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.config.AuthSecurityProperties;
import com.yqz.openblog.config.ClientIpResolver;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.redis.core.RedisOps;
import com.yqz.openblog.user.dto.SliderChallengeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 滑块拉满后由客户端调用 complete，服务端校验与签发 challenge 时一致的 IP，再写入一次性通过标记供登录消费。
 */
@Service
public class SliderVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SliderVerificationService.class);

    private final RedisOps redisOps;
    private final AuthSecurityProperties authSecurityProperties;

    public SliderVerificationService(
            RedisOps redisOps,
            AuthSecurityProperties authSecurityProperties) {
        this.redisOps = redisOps;
        this.authSecurityProperties = authSecurityProperties;
    }

    public SliderChallengeResponse issue(HttpServletRequest request) {
        SliderChallengeResponse r = new SliderChallengeResponse();
        if (!authSecurityProperties.getSlider().isEnabled()) {
            r.setEnabled(false);
            return r;
        }
        String id = UUID.randomUUID().toString();
        String ipSeg = ipSegment(request);
        int ttl = Math.max(30, authSecurityProperties.getSlider().getTtlSeconds());
        redisOps.set(RedisKeys.sliderPending(id), ipSeg, Duration.ofSeconds(ttl));
        r.setEnabled(true);
        r.setChallengeId(id);
        return r;
    }

    public void complete(HttpServletRequest request, String challengeId) {
        if (!authSecurityProperties.getSlider().isEnabled()) {
            return;
        }
        if (challengeId == null || challengeId.isBlank()) {
            throw new BizException(4001, "缺少验证凭证");
        }
        String id = challengeId.trim();
        String pendingKey = RedisKeys.sliderPending(id);
        String storedIp = redisOps.get(pendingKey).orElse(null);
        if (storedIp == null) {
            throw new BizException(4001, "验证已失效，请刷新重试");
        }
        String ipSeg = ipSegment(request);
        if (!storedIp.equals(ipSeg)) {
            throw new BizException(4001, "验证环境异常，请刷新重试");
        }
        redisOps.delete(pendingKey);
        int ttl = Math.max(30, authSecurityProperties.getSlider().getTtlSeconds());
        redisOps.set(RedisKeys.sliderOk(id), "1", Duration.ofSeconds(ttl));
    }

    /**
     * 登录/注册前校验：必须已调用 complete，且仅能使用一次。
     */
    public void verifyAndConsume(String sliderChallengeId) {
        if (!authSecurityProperties.getSlider().isEnabled()) {
            return;
        }
        if (sliderChallengeId == null || sliderChallengeId.isBlank()) {
            try {
                redisOps.hasKey(RedisKeys.SECURITY_SLIDER_HEALTHCHECK);
            } catch (Exception e) {
                return;
            }
            throw new BizException(4001, "请先完成滑动验证");
        }
        String ok = redisOps.getAndDelete(RedisKeys.sliderOk(sliderChallengeId.trim())).orElse(null);
        if (ok == null) {
            throw new BizException(4001, "请先完成滑动验证");
        }
    }

    private static String ipSegment(HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        return ClientIpResolver.toRedisKeySegment(ip);
    }
}
