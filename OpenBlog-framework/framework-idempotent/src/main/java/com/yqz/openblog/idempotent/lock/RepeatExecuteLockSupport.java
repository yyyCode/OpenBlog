package com.yqz.openblog.idempotent.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁封装：非阻塞 tryLock（等待 0 秒），watchdog 自动续期。
 * 故障语义（fail-open）：Redis/Redisson 故障时不抛异常，返回 DEGRADED，由切面降级放行（不阻断业务）。
 */
public class RepeatExecuteLockSupport {

    private static final Logger log = LoggerFactory.getLogger(RepeatExecuteLockSupport.class);

    /** 三态锁获取结果：拿到锁 / 争用（其他请求持有）/ 降级（获取失败，本次放行） */
    public enum LockResult { ACQUIRED, CONTENDED, DEGRADED }

    private final RedissonClient redissonClient;

    public RepeatExecuteLockSupport(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /** 非阻塞尝试获取锁。ACQUIRED=拿到锁，CONTENDED=其他请求正在执行，DEGRADED=锁获取失败（降级放行） */
    public LockResult tryLock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            return lock.tryLock(0, TimeUnit.SECONDS) ? LockResult.ACQUIRED : LockResult.CONTENDED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LockResult.DEGRADED; // 中断：无法得知锁状态 → 降级放行
        } catch (RuntimeException e) {
            log.warn("[idempotent] 分布式锁获取失败，降级放行。lockKey={}", lockKey, e);
            return LockResult.DEGRADED;
        }
    }

    public void unlock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException e) {
            // watchdog 到期会自动释放锁，此处仅记日志，不向调用方传播
            log.warn("[idempotent] 分布式锁释放失败，watchdog 将自动释放。lockKey={}", lockKey, e);
        }
    }
}
