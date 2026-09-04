package com.yqz.openblog.user.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.config.AuthSecurityProperties;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.redis.core.RedisOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 按 IP 统计登录密码错误次数，超过阈值后短期锁定。
 */
@Service
public class LoginLockoutService {

    private static final Logger log = LoggerFactory.getLogger(LoginLockoutService.class);

    private final RedisOps redisOps;
    private final AuthSecurityProperties authSecurityProperties;

    public LoginLockoutService(RedisOps redisOps, AuthSecurityProperties authSecurityProperties) {
        this.redisOps = redisOps;
        this.authSecurityProperties = authSecurityProperties;
    }

    public void assertNotLocked(String ipKeySegment) {
        if (!authSecurityProperties.getLoginLockout().isEnabled()) {
            return;
        }
        try {
            if (redisOps.hasKey(RedisKeys.loginLock(ipKeySegment))) {
                throw new BizException(4292, "登录尝试过于频繁，请稍后再试");
            }
        } catch (BizException e) {
            throw e;
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
        String failKey = RedisKeys.loginFail(ipKeySegment);
        Long c = redisOps.increment(failKey).orElse(null);
        if (c == null) {
            return;
        }
        if (c == 1L) {
            redisOps.expire(failKey, Duration.ofSeconds(Math.max(30, cfg.getFailureWindowSeconds())));
        }
        if (c >= cfg.getMaxFailuresPerIp()) {
            redisOps.set(RedisKeys.loginLock(ipKeySegment), "1",
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
        redisOps.delete(RedisKeys.loginFail(ipKeySegment));
        redisOps.delete(RedisKeys.loginLock(ipKeySegment));
    }

    // ==================== 设备维度（与 IP 维度互补，见 AuthSecurityProperties.DeviceLockout） ====================
    // fpKeySegment 传 null（无指纹设备）一律跳过：该设备不参与设备锁，仍受 IP 锁兜底。

    public void assertNotDeviceLocked(String fpKeySegment) {
        if (fpKeySegment == null || !authSecurityProperties.getDeviceLockout().isEnabled()) {
            return;
        }
        if (redisOps.hasKey(RedisKeys.deviceLock(fpKeySegment))) {
            throw new BizException(4292, "该设备登录尝试过于频繁，请稍后再试");
        }
    }

    /**
     * 密码校验失败时调用（设备指纹维度）。
     */
    public void recordDevicePasswordFailure(String fpKeySegment) {
        if (fpKeySegment == null || !authSecurityProperties.getDeviceLockout().isEnabled()) {
            return;
        }
        var cfg = authSecurityProperties.getDeviceLockout();
        String failKey = RedisKeys.deviceFail(fpKeySegment);
        Long c = redisOps.increment(failKey).orElse(null);
        if (c == null) {
            return;
        }
        if (c == 1L) {
            redisOps.expire(failKey, Duration.ofSeconds(Math.max(30, cfg.getFailureWindowSeconds())));
        }
        if (c >= cfg.getMaxFailuresPerFp()) {
            redisOps.set(RedisKeys.deviceLock(fpKeySegment), "1",
                    Duration.ofSeconds(Math.max(60, cfg.getLockoutSeconds())));
        }
    }

    /**
     * 登录成功时清除该设备的失败计数与锁定（若存在）。
     */
    public void clearDeviceFailures(String fpKeySegment) {
        if (fpKeySegment == null || !authSecurityProperties.getDeviceLockout().isEnabled()) {
            return;
        }
        redisOps.delete(RedisKeys.deviceFail(fpKeySegment));
        redisOps.delete(RedisKeys.deviceLock(fpKeySegment));
    }
}
