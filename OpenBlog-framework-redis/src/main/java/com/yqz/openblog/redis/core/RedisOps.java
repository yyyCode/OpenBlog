package com.yqz.openblog.redis.core;

/**
 * Redis 操作封装接口 — 内置容错，调用方无需关心 Redis 连接异常。
 * <ul>
 *   <li>读操作返回 {@link java.util.Optional#empty()} 表示 miss 或 Redis 故障</li>
 *   <li>写操作 void，故障时内部打 warn 日志</li>
 *   <li>布尔操作失败返回 false（fail-safe）</li>
 * </ul>
 */
public interface RedisOps {

    java.util.Optional<String> get(String key);

    void set(String key, String value, java.time.Duration ttl);

    void delete(String key);

    boolean setIfAbsent(String key, String value, java.time.Duration ttl);

    java.util.Optional<Long> increment(String key);

    void expire(String key, java.time.Duration ttl);

    boolean hasKey(String key);

    java.util.Optional<String> getAndDelete(String key);
}
