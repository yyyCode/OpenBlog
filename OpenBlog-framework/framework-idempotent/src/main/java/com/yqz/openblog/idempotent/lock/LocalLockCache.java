package com.yqz.openblog.idempotent.lock;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地锁池：同 Key 复用同一把 ReentrantLock 实例，避免每次 new 锁导致互斥失效。
 * Caffeine get 线程安全，同 Key 只创建一次。
 */
public class LocalLockCache {

    private final Cache<String, ReentrantLock> cache;

    public LocalLockCache(int expireHours) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(expireHours, TimeUnit.HOURS)
                .build();
    }

    public ReentrantLock getLock(String key) {
        return cache.get(key, k -> new ReentrantLock());
    }
}
