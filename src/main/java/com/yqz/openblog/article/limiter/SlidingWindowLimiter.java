package com.yqz.openblog.article.limiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Redis ZSET 的滑动窗口限流（Lua 保证原子性）。
 *
 * 逻辑：每次尝试时先删除窗口外数据，再统计窗口内数量；
 * 若数量 < limit，则把当前时间写入 ZSET 并返回 allowed=1。
 */
@Component
public class SlidingWindowLimiter {

    private static final long EXPIRE_EXTRA_MS = 10_000L;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public SlidingWindowLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setResultType(Long.class);
        this.script.setScriptText(
                "local key = KEYS[1]; " +
                        "local now = tonumber(ARGV[1]); " +
                        "local windowMs = tonumber(ARGV[2]); " +
                        "local limit = tonumber(ARGV[3]); " +
                        "local member = ARGV[4]; " +
                        "redis.call('ZREMRANGEBYSCORE', key, 0, now-windowMs); " +
                        "local count = redis.call('ZCARD', key); " +
                        "if count < limit then " +
                        "  redis.call('ZADD', key, now, member); " +
                        "  redis.call('PEXPIRE', key, windowMs + " + EXPIRE_EXTRA_MS + "); " +
                        "  return 1; " +
                        "end; " +
                        "return 0;"
        );
    }

    /**
     * @param key Redis key（区分文章与 viewerKey）
     * @param windowMs 滑动窗口大小（毫秒）
     * @param limit 同一 viewer 在窗口内允许的次数
     * @param nowMs 当前时间戳（毫秒）
     * @param member ZSET 成员（保证唯一即可）
     * @return true=允许计数，false=限流
     */
    public boolean tryAcquire(String key, long windowMs, int limit, long nowMs, String member) {
        Long allowed = redisTemplate.execute(
                script,
                List.of(key),
                String.valueOf(nowMs),
                String.valueOf(windowMs),
                String.valueOf(limit),
                member
        );
        return allowed != null && allowed == 1L;
    }
}

