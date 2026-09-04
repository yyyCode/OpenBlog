package com.yqz.openblog.user.service;

import com.yqz.openblog.redis.core.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 账号 ↔ 设备指纹绑定记录（设备指纹防构造第 3 层）。
 * <p>
 * 登录成功后把本次指纹记为该账号的「已知设备」（Redis SET，值=已过网关格式校验的指纹）。
 * 目的不是立刻拦截（新设备二次验证不在本期），而是留下可观测的绑定事实：日志可见「新设备首次登录」，
 * 后续演进（新设备 + 新 IP → 二次验证、异常设备告警）以本集合为基线。
 * <p>
 * 契约：只作记录，不做判定——Redis 故障仅打 warn、绝不阻断登录（与全局 fail-open 一致）。
 */
@Service
public class AccountDeviceService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeviceService.class);
    /** 已知设备集合保留时长：指纹是弱信号，按 90 天滑动保留即可。 */
    private static final Duration DEVICE_TTL = Duration.ofDays(90);

    private final StringRedisTemplate redisTemplate;

    public AccountDeviceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 登录成功后把指纹记为该账号已知设备。
     *
     * @param userId 账号 id
     * @param fp     已通过格式校验（{@code ^[A-Za-z0-9-]{16,64}$}）的设备指纹；null/空则跳过
     */
    public void recordLogin(Long userId, String fp) {
        if (userId == null || fp == null || fp.isBlank()) {
            return;
        }
        try {
            String key = RedisKeys.accountDevices(userId);
            Long added = redisTemplate.opsForSet().add(key, fp);
            // 每次登录都滚动续 TTL：活跃账号的"已知设备"集合不会因 90 天静默消失；停用 90 天后自然过期清理
            redisTemplate.expire(key, DEVICE_TTL);
            if (added != null && added > 0) {
                log.info("account first-seen device login: userId={} fp={}", userId, mask(fp));
            }
        } catch (RuntimeException e) {
            // Redis 不可用 → 仅记录失败，登录放行（fail-open）
            log.warn("record account device failed, allow login: userId={} err={}", userId, e.toString());
        }
    }

    /** 日志脱敏：只留首 6 + 末 2 字符，指纹也是标识符，不全量落日志。 */
    private static String mask(String fp) {
        return fp.length() <= 8 ? fp : fp.substring(0, 6) + ".." + fp.substring(fp.length() - 2);
    }
}
