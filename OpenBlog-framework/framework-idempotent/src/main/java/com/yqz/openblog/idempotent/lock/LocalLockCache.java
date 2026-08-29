package com.yqz.openblog.idempotent.lock;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地锁池：同 Key 复用同一把 ReentrantLock 实例，避免每次 new 锁导致互斥失效。
 * Caffeine get 线程安全，同 Key 只创建一次。
 * 注意：本地锁是"优化层"，锁实例被驱逐期间同一 Key 可能短暂失去本地互斥，
 * 正确性由 Redisson 分布式锁 + 双重检测兜底（见设计文档 §6）。
 */
public class LocalLockCache {

    private static final long MAX_LOCKS = 10_000;

    private final Cache<String, ReentrantLock> cache;

    public LocalLockCache(int expireHours) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_LOCKS)
                .expireAfterAccess(expireHours, TimeUnit.HOURS)
                .build();
    }

    public ReentrantLock getLock(String key) {
        return cache.get(key, k -> new ReentrantLock());
    }
}
