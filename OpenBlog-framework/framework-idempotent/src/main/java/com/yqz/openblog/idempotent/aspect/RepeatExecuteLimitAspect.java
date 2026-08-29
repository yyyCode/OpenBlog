package com.yqz.openblog.idempotent.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.idempotent.annotation.RepeatExecuteLimit;
import com.yqz.openblog.idempotent.constant.RepeatExecuteLimitConstant;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyBuilder;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyResolver;
import com.yqz.openblog.idempotent.handle.RepeatExecuteFlagStore;
import com.yqz.openblog.idempotent.lock.LocalLockCache;
import com.yqz.openblog.idempotent.lock.RepeatExecuteLockSupport;
import com.yqz.openblog.idempotent.strategy.IdempotentStrategy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 幂等核心切面。@Order(-11) 保证在 @Transactional 之外（最外层）。
 * 流程：① 快路径查标识 → ③ 本地锁 → ④ Redisson 分布式锁 → ⑤ 双重检测 → ⑥ 执行 → ⑦ 成功写标识。
 */
@Aspect
@Order(-11)
public class RepeatExecuteLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RepeatExecuteLimitAspect.class);

    private final RepeatExecuteKeyResolver keyResolver;
    private final RepeatExecuteKeyBuilder keyBuilder;
    private final RepeatExecuteLockSupport lockSupport;
    private final LocalLockCache localLockCache;
    private final RepeatExecuteFlagStore flagStore;
    private final ObjectMapper objectMapper;

    public RepeatExecuteLimitAspect(RepeatExecuteKeyResolver keyResolver,
                                    RepeatExecuteKeyBuilder keyBuilder,
                                    RepeatExecuteLockSupport lockSupport,
                                    LocalLockCache localLockCache,
                                    RepeatExecuteFlagStore flagStore,
                                    ObjectMapper objectMapper) {
        this.keyResolver = keyResolver;
        this.keyBuilder = keyBuilder;
        this.lockSupport = lockSupport;
        this.localLockCache = localLockCache;
        this.flagStore = flagStore;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(repeatLimit)")
    public Object around(ProceedingJoinPoint pjp, RepeatExecuteLimit repeatLimit) throws Throwable {
        String name = repeatLimit.name();
        List<String> keys = keyResolver.resolve(pjp, repeatLimit.keys());
        // 防御：任一幂等 Key 为空则跳过幂等保护（记录警告），避免错误配置锁死业务
        if (name.isBlank() || keys.isEmpty() || keys.stream().anyMatch(String::isBlank)) {
            log.warn("[idempotent] 幂等 Key 为空，跳过幂等保护。name={}, keys={}", name, keys);
            return pjp.proceed();
        }

        String lockKey = keyBuilder.lockKey(name, keys);
        String flagKey = keyBuilder.flagKey(name, keys);
        String resultKey = keyBuilder.resultKey(name, keys);
        long duration = repeatLimit.durationTime();
        Class<?> returnType = ((MethodSignature) pjp.getSignature()).getReturnType();

        // ① 快路径：标识已存在 → 直接按策略处理（不抢锁，O(1)）
        if (flagStore.isSuccess(flagKey)) {
            return handleHit(repeatLimit, resultKey, returnType);
        }

        // ③ 本地锁：同 JVM 内串行
        ReentrantLock localLock = localLockCache.getLock(lockKey);
        if (!localLock.tryLock()) {
            return handleHit(repeatLimit, resultKey, returnType);
        }
        try {
            // ④ 分布式锁：跨 JVM 串行，非阻塞
            // CONTENDED → 防重命中；ACQUIRED 与 DEGRADED 都继续执行（DEGRADED = Redis 故障降级放行）
            if (lockSupport.tryLock(lockKey) == RepeatExecuteLockSupport.LockResult.CONTENDED) {
                return handleHit(repeatLimit, resultKey, returnType);
            }
            try {
                // ⑤ 双重检测：获锁后再查一次标识，杜绝竞争窗口
                if (flagStore.isSuccess(flagKey)) {
                    return handleHit(repeatLimit, resultKey, returnType);
                }
                // ⑥ 执行（事务在最内层，proceed 返回即已提交）
                Object result = pjp.proceed();
                // ⑦ 成功后才写标识
                if (duration > 0) {
                    if (repeatLimit.strategy() == IdempotentStrategy.RETURN_SAME_RESULT) {
                        try {
                            flagStore.setResult(resultKey, objectMapper.writeValueAsString(result), duration);
                        } catch (Exception e) {
                            log.warn("[idempotent] 写结果标识失败（已忽略）。name={}", name, e);
                        }
                    }
                    try {
                        flagStore.setFlag(flagKey, duration);
                    } catch (Exception e) {
                        log.warn("[idempotent] 写幂等标识失败（已忽略）。name={}", name, e);
                    }
                }
                return result;
            } finally {
                lockSupport.unlock(lockKey);
            }
        } finally {
            localLock.unlock();
        }
    }

    private Object handleHit(RepeatExecuteLimit repeatLimit, String resultKey, Class<?> returnType) {
        if (repeatLimit.strategy() == IdempotentStrategy.RETURN_SAME_RESULT) {
            Optional<String> json = flagStore.getResult(resultKey);
            if (json.isPresent()) {
                try {
                    return objectMapper.readValue(json.get(), returnType);
                } catch (Exception e) {
                    log.warn("[idempotent] 结果反序列化失败，退化为拒绝。resultKey={}", resultKey, e);
                }
            }
            // 结果尚未落盘 / 反序列化失败 → 退化为拒绝
        }
        throw new BizException(RepeatExecuteLimitConstant.REJECT_CODE, repeatLimit.message());
    }
}
