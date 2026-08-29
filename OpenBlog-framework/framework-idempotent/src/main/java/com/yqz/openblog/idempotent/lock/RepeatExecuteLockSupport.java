package com.yqz.openblog.idempotent.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁封装：非阻塞 tryLock（等待 0 秒），watchdog 自动续期。
 */
public class RepeatExecuteLockSupport {

    private final RedissonClient redissonClient;

    public RepeatExecuteLockSupport(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /** 非阻塞尝试获取锁。true=拿到锁，false=别的请求正在执行或锁获取失败 */
    public boolean tryLock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
