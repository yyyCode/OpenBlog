package com.yqz.openblog.idempotent.handle;

import java.util.Optional;

/**
 * 幂等标识存储抽象。接口化便于测试（测试注入内存实现），生产用 Redis 实现。
 * 约定：任何读/写失败都不抛异常——读返回 empty（视为未命中），写静默降级。
 */
public interface RepeatExecuteFlagStore {

    /** 幂等标识是否已写入 success */
    boolean isSuccess(String flagKey);

    /** 写入 success 标识，TTL 秒；ttlSeconds<=0 时静默忽略写入（调用方需保证正 TTL） */
    void setFlag(String flagKey, long ttlSeconds);

    /** 读取上次结果 JSON（RETURN_SAME_RESULT 用） */
    Optional<String> getResult(String resultKey);

    /** 写入结果 JSON，TTL 秒；ttlSeconds<=0 时静默忽略写入（调用方需保证正 TTL） */
    void setResult(String resultKey, String json, long ttlSeconds);
}
