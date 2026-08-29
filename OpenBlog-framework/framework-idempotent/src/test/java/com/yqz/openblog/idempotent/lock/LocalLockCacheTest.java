package com.yqz.openblog.idempotent.lock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLockCacheTest {

    @Test
    void reusesSameLockForSameKey() {
        LocalLockCache cache = new LocalLockCache(48);
        ReentrantLock a = cache.getLock("k1");
        ReentrantLock b = cache.getLock("k1");
        assertSame(a, b);
        assertTrue(a.tryLock());
        a.unlock();
    }

    @Test
    void differentKeysGetDifferentLocks() {
        LocalLockCache cache = new LocalLockCache(48);
        assertNotNull(cache.getLock("k1"));
        assertNotNull(cache.getLock("k2"));
        assertTrue(cache.getLock("k1") != cache.getLock("k2"));
    }
}
