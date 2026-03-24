package com.yqz.openblog.user.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.config.AuthSecurityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 按 IP 统计登录密码错误次数，超过阈值后短期锁定。
 */
@Service
public class LoginLockoutService {

    private static final String FAIL_PREFIX = "openblog:auth:fail:ip:";
    private static final String LOCK_PREFIX = "openblog:auth:lock:ip:";

    private final StringRedisTemplate redisTemplate;
    private final AuthSecurityProperties authSecurityProperties;

    public LoginLockoutService(StringRedisTemplate redisTemplate, AuthSecurityProperties authSecurityProperties) {
        this.redisTemplate = redisTemplate;
        this.authSecurityProperties = authSecurityProperties;
    }

    public void assertNotLocked(String ipKeySegment) {
        if (!authSecurityProperties.getLoginLockout().isEnabled()) {
            return;
        }
        String lockKey = LOCK_PREFIX + ipKeySegment;
        Boolean locked = redisTemplate.hasKey(lockKey);
        if (Boolean.TRUE.equals(locked)) {
            throw new BizException(4292, "登录尝试过于频繁，请稍后再试");
        }
    }

    /**
     * 密码校验失败时调用。
     */
    public void recordPasswordFailure(String ipKeySegment) {
        if (!authSecurityProperties.getLoginLockout().isEnabled()) {
            return;
        }
        var cfg = authSecurityProperties.getLoginLockout();
        String failKey = FAIL_PREFIX + ipKeySegment;
        Long c = redisTemplate.opsForValue().increment(failKey);
        if (c != null && c == 1L) {
            redisTemplate.expire(failKey, Duration.ofSeconds(Math.max(30, cfg.getFailureWindowSeconds())));
        }
        if (c != null && c >= cfg.getMaxFailuresPerIp()) {
            redisTemplate.opsForValue().set(
                    LOCK_PREFIX + ipKeySegment,
                    "1",
                    Duration.ofSeconds(Math.max(60, cfg.getLockoutSeconds())));
        }
    }

    /**
     * 登录成功时清除该 IP 的失败计数与锁定（若存在）。
     */
    public void clearFailures(String ipKeySegment) {
        if (!authSecurityProperties.getLoginLockout().isEnabled()) {
            return;
        }
        redisTemplate.delete(FAIL_PREFIX + ipKeySegment);
        redisTemplate.delete(LOCK_PREFIX + ipKeySegment);
    }
}
