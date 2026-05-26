package com.yqz.openblog.redis.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * {@link RedisOps} 的默认实现，基于 {@link StringRedisTemplate}。
 * 所有方法内置 try-catch，Redis 不可用时降级返回安全默认值。
 */
public class DefaultRedisOps implements RedisOps {

    private static final Logger log = LoggerFactory.getLogger(DefaultRedisOps.class);

    private final StringRedisTemplate redisTemplate;

    public DefaultRedisOps(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (Exception e) {
            log.warn("Redis get 失败，key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis set 失败（已忽略），key={}", key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete 失败（已忽略），key={}", key, e);
        }
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
        } catch (Exception e) {
            log.warn("Redis setIfAbsent 失败，key={}", key, e);
            return false;
        }
    }

    @Override
    public Optional<Long> increment(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().increment(key));
        } catch (Exception e) {
            log.warn("Redis increment 失败，key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void expire(String key, Duration ttl) {
        try {
            redisTemplate.expire(key, ttl);
        } catch (Exception e) {
            log.warn("Redis expire 失败（已忽略），key={}", key, e);
        }
    }

    @Override
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Redis hasKey 失败，key={}", key, e);
            return false;
        }
    }

    @Override
    public Optional<String> getAndDelete(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(key));
        } catch (Exception e) {
            log.warn("Redis getAndDelete 失败，key={}", key, e);
            return Optional.empty();
        }
    }
}
