package com.yqz.openblog.idempotent.handle;

import com.yqz.openblog.idempotent.constant.RepeatExecuteLimitConstant;
import com.yqz.openblog.redis.core.RedisOps;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于项目既有 RedisOps（StringRedisTemplate 封装）的标识存储。
 * RedisOps 已内置容错：读返回 empty、写打 warn，组件整体对 Redis 故障降级。
 */
public class RedisRepeatExecuteFlagStore implements RepeatExecuteFlagStore {

    private final RedisOps redisOps;

    public RedisRepeatExecuteFlagStore(RedisOps redisOps) {
        this.redisOps = redisOps;
    }

    @Override
    public boolean isSuccess(String flagKey) {
        return redisOps.get(flagKey)
                .map(RepeatExecuteLimitConstant.FLAG_SUCCESS::equals)
                .orElse(false);
    }

    @Override
    public void setFlag(String flagKey, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        redisOps.set(flagKey, RepeatExecuteLimitConstant.FLAG_SUCCESS, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<String> getResult(String resultKey) {
        return redisOps.get(resultKey);
    }

    @Override
    public void setResult(String resultKey, String json, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        redisOps.set(resultKey, json, Duration.ofSeconds(ttlSeconds));
    }
}
