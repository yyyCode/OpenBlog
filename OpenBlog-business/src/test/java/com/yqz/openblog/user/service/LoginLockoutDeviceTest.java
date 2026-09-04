package com.yqz.openblog.user.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.config.AuthSecurityProperties;
import com.yqz.openblog.redis.core.RedisKeys;
import com.yqz.openblog.redis.core.RedisOps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备级失败封禁（第 3 层）单测：LoginLockoutService 的 deviceLockout 维度方法。
 * 与 IP 维度同构但 key 按设备指纹分段；fpSeg 传 null（无指纹设备）一律跳过。
 */
@ExtendWith(MockitoExtension.class)
class LoginLockoutDeviceTest {

    private static final String FP = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6";
    private static final String FP_2 = "f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1";

    @Mock private RedisOps redisOps;
    @Spy private AuthSecurityProperties authSecurityProperties = new AuthSecurityProperties();

    private LoginLockoutService service;

    @BeforeEach
    void setUp() {
        service = new LoginLockoutService(redisOps, authSecurityProperties);
    }

    @Test
    void assertNotDeviceLocked_locked_throws4292() {
        when(redisOps.hasKey(RedisKeys.deviceLock(FP))).thenReturn(true);
        BizException ex = assertThrows(BizException.class, () -> service.assertNotDeviceLocked(FP));
        assertEquals(4292, ex.getCode());
    }

    @Test
    void assertNotDeviceLocked_unlockedOrNullFp_passes() {
        when(redisOps.hasKey(RedisKeys.deviceLock(FP))).thenReturn(false);
        service.assertNotDeviceLocked(FP); // 未锁定 → 不抛
        service.assertNotDeviceLocked(null); // 无指纹设备 → 不查 Redis
        verify(redisOps).hasKey(RedisKeys.deviceLock(FP));
    }

    @Test
    void recordDevicePasswordFailure_redistMiss_doesNothing() {
        when(redisOps.increment(RedisKeys.deviceFail(FP))).thenReturn(Optional.empty());
        service.recordDevicePasswordFailure(FP);
        verify(redisOps, never()).expire(anyString(), any());
        verify(redisOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    void recordDevicePasswordFailure_atThreshold_locksDevice() {
        // 第 5 次失败（c >= maxFailuresPerFp）→ 写设备锁 900s
        when(redisOps.increment(RedisKeys.deviceFail(FP))).thenReturn(Optional.of(5L));
        service.recordDevicePasswordFailure(FP);
        verify(redisOps).set(RedisKeys.deviceLock(FP), "1", Duration.ofSeconds(900));
    }

    @Test
    void recordDevicePasswordFailure_firstFailure_setsWindowTtl() {
        when(redisOps.increment(RedisKeys.deviceFail(FP))).thenReturn(Optional.of(1L));
        service.recordDevicePasswordFailure(FP);
        verify(redisOps).expire(RedisKeys.deviceFail(FP), Duration.ofSeconds(300));
    }

    @Test
    void recordDevicePasswordFailure_perFpIsolated() {
        // 不同设备指纹各计各的：FP_2 首次失败不影响 FP 的阈值判定
        when(redisOps.increment(RedisKeys.deviceFail(FP_2))).thenReturn(Optional.of(1L));
        service.recordDevicePasswordFailure(FP_2);
        verify(redisOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    void clearDeviceFailures_deletesFailAndLock() {
        service.clearDeviceFailures(FP);
        verify(redisOps).delete(RedisKeys.deviceFail(FP));
        verify(redisOps).delete(RedisKeys.deviceLock(FP));
    }

    @Test
    void disabled_deviceLockoutDoesNothing() {
        authSecurityProperties.getDeviceLockout().setEnabled(false);
        service.assertNotDeviceLocked(FP);
        service.recordDevicePasswordFailure(FP);
        service.clearDeviceFailures(FP);
        verify(redisOps, never()).hasKey(anyString());
        verify(redisOps, never()).increment(anyString());
    }
}
