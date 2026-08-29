package com.yqz.openblog.idempotent.lock;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepeatExecuteLockSupportTest {

    private RepeatExecuteLockSupport supportWith(RLock lock) {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getLock("k")).thenReturn(lock);
        return new RepeatExecuteLockSupport(client);
    }

    @Test
    void tryLock_acquired() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        assertEquals(RepeatExecuteLockSupport.LockResult.ACQUIRED, supportWith(lock).tryLock("k"));
    }

    @Test
    void tryLock_contended() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);
        assertEquals(RepeatExecuteLockSupport.LockResult.CONTENDED, supportWith(lock).tryLock("k"));
    }

    @Test
    void tryLock_redissonFailure_degrades() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new RuntimeException("connection lost"));
        assertEquals(RepeatExecuteLockSupport.LockResult.DEGRADED, supportWith(lock).tryLock("k"));
    }

    @Test
    void tryLock_interrupted_degradesAndRestoresInterruptFlag() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        assertEquals(RepeatExecuteLockSupport.LockResult.DEGRADED, supportWith(lock).tryLock("k"));
        assertTrue(Thread.interrupted());
    }

    @Test
    void unlock_swallowsReleaseFailure() {
        RLock lock = mock(RLock.class);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(new RuntimeException("connection lost")).when(lock).unlock();
        supportWith(lock).unlock("k"); // 不得抛出
        verify(lock).unlock();
    }

    @Test
    void unlock_skipsWhenNotHeld() {
        RLock lock = mock(RLock.class);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        supportWith(lock).unlock("k");
        verify(lock, never()).unlock();
    }
}
