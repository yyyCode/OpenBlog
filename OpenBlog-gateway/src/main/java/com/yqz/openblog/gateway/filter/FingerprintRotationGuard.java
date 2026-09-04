package com.yqz.openblog.gateway.filter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 指纹防构造守卫（配合 RateLimitFilter 的 FP_IP scope）。
 * 客户端指纹是自报 header，脚本可每请求伪造一个"格式合法"的新指纹绕过复合限流桶
 * （key={fp}_{ip}_{path}，换 fp 即换新桶）。本守卫按 IP 统计滑动窗口内"不同指纹"基数：
 * 同一 IP 在窗口内已见 ≥ budget 个指纹、又出现**全新指纹** → 判定疑似轮换，调用方拒绝该请求。
 * <p>
 * Lua 保证"去旧→判新→计数→写入"原子；新指纹超预算时**不写入**，集合大小恒 ≤ budget，
 * Redis key 有界且自带 TTL，恶意洪泛无法撑爆 Redis。
 * <p>
 * 契约：Redis 故障/返回异常一律视为放行（false），与 SlidingWindowLimiter 同 fail-open，绝不 500。
 */
@Component
public class FingerprintRotationGuard {

    private static final long EXPIRE_EXTRA_MS = 10_000L;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public FingerprintRotationGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setResultType(Long.class);
        this.script.setScriptText(
                "local key = KEYS[1]; " +
                        "local now = tonumber(ARGV[1]); " +
                        "local windowMs = tonumber(ARGV[2]); " +
                        "local budget = tonumber(ARGV[3]); " +
                        "local member = ARGV[4]; " +
                        "redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs); " +
                        "if redis.call('ZSCORE', key, member) then return 0; end; " +
                        "local count = redis.call('ZCARD', key); " +
                        "if count >= budget then return 1; end; " +
                        "redis.call('ZADD', key, now, member); " +
                        "redis.call('PEXPIRE', key, windowMs + " + EXPIRE_EXTRA_MS + "); " +
                        "return 0;"
        );
    }

    /**
     * 判定该 IP 上出现全新指纹是否已超预算（疑似轮换）。
     *
     * @param ip       客户端真实 IP（由边缘 Nginx $remote_addr 覆写，权威，不可客户端伪造）
     * @param fp       已通过格式校验（{@code ^[A-Za-z0-9-]{16,64}$}）的设备指纹
     * @param windowMs 去重统计窗口（毫秒）
     * @param budget   窗口内允许的不同指纹数上限；≤0 视为关闭（恒放行）
     * @return true=该指纹疑似轮换，应拒绝；false=放行
     */
    public boolean isOverBudget(String ip, String fp, long windowMs, int budget) {
        if (budget <= 0) {
            return false;
        }
        try {
            Long over = redisTemplate.execute(
                    script,
                    List.of("gateway:fpset:" + ip),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(windowMs),
                    String.valueOf(budget),
                    fp
            );
            return over != null && over == 1L;
        } catch (RuntimeException e) {
            // Redis 不可用 → 放行（fail-open），与网关整体降级契约一致
            return false;
        }
    }
}
