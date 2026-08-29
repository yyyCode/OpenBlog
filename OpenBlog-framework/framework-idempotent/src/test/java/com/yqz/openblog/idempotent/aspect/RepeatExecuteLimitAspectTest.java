package com.yqz.openblog.idempotent.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.idempotent.annotation.RepeatExecuteLimit;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyBuilder;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyResolver;
import com.yqz.openblog.idempotent.handle.InMemoryRepeatExecuteFlagStore;
import com.yqz.openblog.idempotent.handle.RepeatExecuteFlagStore;
import com.yqz.openblog.idempotent.lock.LocalLockCache;
import com.yqz.openblog.idempotent.lock.RepeatExecuteLockSupport;
import com.yqz.openblog.idempotent.strategy.IdempotentStrategy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepeatExecuteLimitAspectTest {

    static class Result {
        private String text;

        public Result() {
        }

        public Result(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    static class TestReq {
        private String requestId;

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
    }

    static class Target {
        @RepeatExecuteLimit(name = "comment_create", keys = {"#articleId", "#uid", "#req.requestId"},
                durationTime = 30, strategy = IdempotentStrategy.RETURN_SAME_RESULT)
        public Result create(Long articleId, Long uid, TestReq req) {
            return new Result("ok");
        }

        @RepeatExecuteLimit(name = "comment_reply", keys = {"#commentId", "#uid", "#req.requestId"},
                durationTime = 30)
        public Result reply(Long commentId, Long uid, TestReq req) {
            return new Result("ok");
        }

        @RepeatExecuteLimit(name = "comment_create", keys = {"#articleId", "#uid", "#req.requestId"},
                durationTime = 0, strategy = IdempotentStrategy.RETURN_SAME_RESULT)
        public Result createNoWindow(Long articleId, Long uid, TestReq req) {
            return new Result("ok");
        }
    }

    private static final String FLAG_CREATE = "openblog:test:repeat:flag:comment_create:42:7:r1";
    private static final String RESULT_CREATE = "openblog:test:repeat:result:comment_create:42:7:r1";
    private static final String FLAG_REPLY = "openblog:test:repeat:flag:comment_reply:42:7:r1";

    private RepeatExecuteLimitAspect aspect;
    private RepeatExecuteFlagStore flagStore;
    private RepeatExecuteLockSupport lockSupport;
    private LocalLockCache localLockCache;
    private ProceedingJoinPoint pjp;
    private MethodSignature sig;

    @BeforeEach
    void setUp() {
        flagStore = new InMemoryRepeatExecuteFlagStore();
        lockSupport = mock(RepeatExecuteLockSupport.class);
        localLockCache = new LocalLockCache(48);
        aspect = new RepeatExecuteLimitAspect(
                new RepeatExecuteKeyResolver(),
                new RepeatExecuteKeyBuilder("test"),
                lockSupport,
                localLockCache,
                flagStore,
                new ObjectMapper());
        pjp = mock(ProceedingJoinPoint.class);
        sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
    }

    private RepeatExecuteLimit annotationOf(String methodName, Class<?>... paramTypes) throws Exception {
        return Target.class.getMethod(methodName, paramTypes).getAnnotation(RepeatExecuteLimit.class);
    }

    private void stubInvocation(String methodName, Class<?>[] paramTypes, Object[] args,
                                Class<?> returnType, Object result) throws Throwable {
        Method m = Target.class.getMethod(methodName, paramTypes);
        when(sig.getMethod()).thenReturn(m);
        when(sig.getReturnType()).thenReturn(returnType);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed()).thenReturn(result);
    }

    private TestReq reqWith(String requestId) {
        TestReq req = new TestReq();
        req.setRequestId(requestId);
        return req;
    }

    @Test
    void fastPathReject_CACHE_REJECT_throwsBizException() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        flagStore.setFlag(FLAG_REPLY, 30);

        assertThrows(BizException.class, () -> aspect.around(pjp, annotationOf("reply", params)));
        verify(lockSupport, never()).tryLock(anyString());
        verify(pjp, never()).proceed();
    }

    @Test
    void fastPathHit_RETURN_SAME_RESULT_returnsCached() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("create", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        flagStore.setFlag(FLAG_CREATE, 30);
        flagStore.setResult(RESULT_CREATE, "{\"text\":\"first\"}", 30);

        Object out = aspect.around(pjp, annotationOf("create", params));

        assertInstanceOf(Result.class, out);
        assertEquals("first", ((Result) out).text);
        verify(pjp, never()).proceed();
    }

    @Test
    void returnSame_noCachedResultYet_throws() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("create", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        flagStore.setFlag(FLAG_CREATE, 30);

        assertThrows(BizException.class, () -> aspect.around(pjp, annotationOf("create", params)));
    }

    @Test
    void doubleCheck_afterLock_flagAlreadySuccess_returnsCached() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("create", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.ACQUIRED);

        // 快路径 miss（第一次 isSuccess=false），双重检测 hit（第二次 isSuccess=true），
        // 模拟"等待锁期间另一请求已完成并写入标识"的经典竞争窗口。
        RepeatExecuteFlagStore flagStoreMock = mock(RepeatExecuteFlagStore.class);
        when(flagStoreMock.isSuccess(anyString()))
                .thenReturn(false)
                .thenReturn(true);
        when(flagStoreMock.getResult(RESULT_CREATE)).thenReturn(Optional.of("{\"text\":\"first\"}"));
        aspect = new RepeatExecuteLimitAspect(
                new RepeatExecuteKeyResolver(),
                new RepeatExecuteKeyBuilder("test"),
                lockSupport,
                localLockCache,
                flagStoreMock,
                new ObjectMapper());

        Object out = aspect.around(pjp, annotationOf("create", params));

        assertEquals("first", ((Result) out).text);
        verify(pjp, never()).proceed();
        verify(lockSupport).unlock(anyString());
    }

    @Test
    void localLockContended_rejects() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));

        // ReentrantLock 是可重入锁：同一线程重复 tryLock 必然成功，
        // 因此必须由另一线程持锁才能真正模拟"本地锁争用"。
        ReentrantLock held = localLockCache.getLock("openblog:test:repeat:lock:comment_reply:42:7:r1");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            held.lock();
            locked.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                held.unlock();
            }
        });
        holder.start();
        try {
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            assertThrows(BizException.class, () -> aspect.around(pjp, annotationOf("reply", params)));
        } finally {
            release.countDown();
            holder.join(5000);
        }
        verify(pjp, never()).proceed();
    }

    @Test
    void distributedLockContended_rejects() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.CONTENDED);

        assertThrows(BizException.class, () -> aspect.around(pjp, annotationOf("reply", params)));
        verify(lockSupport, never()).unlock(anyString());
        verify(pjp, never()).proceed();
    }

    @Test
    void distributedLockDegraded_proceeds() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.DEGRADED);

        Object out = aspect.around(pjp, annotationOf("reply", params));

        assertEquals("ok", ((Result) out).text);
        verify(pjp).proceed();
        assertTrue(flagStore.isSuccess(FLAG_REPLY));
        verify(lockSupport).unlock(anyString());
    }

    @Test
    void success_RETURN_SAME_RESULT_writesFlagAndResult() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("create", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.ACQUIRED);

        Object out = aspect.around(pjp, annotationOf("create", params));

        assertEquals("ok", ((Result) out).text);
        assertTrue(flagStore.isSuccess(FLAG_CREATE));
        assertTrue(flagStore.getResult(RESULT_CREATE).isPresent());
        verify(lockSupport).unlock(anyString());
    }

    @Test
    void success_RETURN_SAME_RESULT_writesResultBeforeFlag() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        Method m = Target.class.getMethod("create", params);
        when(sig.getMethod()).thenReturn(m);
        when(sig.getReturnType()).thenReturn(Result.class);
        when(pjp.getArgs()).thenReturn(new Object[]{42L, 7L, reqWith("r1")});
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.ACQUIRED);
        when(pjp.proceed()).thenReturn(new Result("ok"));

        RepeatExecuteFlagStore mockStore = mock(RepeatExecuteFlagStore.class);
        when(mockStore.isSuccess(anyString())).thenReturn(false);
        aspect = new RepeatExecuteLimitAspect(
                new RepeatExecuteKeyResolver(),
                new RepeatExecuteKeyBuilder("test"),
                lockSupport,
                localLockCache,
                mockStore,
                new ObjectMapper());

        aspect.around(pjp, annotationOf("create", params));

        InOrder inOrder = inOrder(mockStore);
        inOrder.verify(mockStore).setResult(eq(RESULT_CREATE), anyString(), eq(30L));
        inOrder.verify(mockStore).setFlag(eq(FLAG_CREATE), eq(30L));
    }

    @Test
    void durationZero_noFlagWritten() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("createNoWindow", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.ACQUIRED);

        Object out = aspect.around(pjp, annotationOf("createNoWindow", params));

        assertEquals("ok", ((Result) out).text);
        assertFalse(flagStore.isSuccess(FLAG_CREATE));
    }

    @Test
    void success_CACHE_REJECT_writesFlagOnly() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.ACQUIRED);

        aspect.around(pjp, annotationOf("reply", params));

        assertTrue(flagStore.isSuccess(FLAG_REPLY));
        assertFalse(flagStore.getResult("openblog:test:repeat:result:comment_reply:42:7:r1").isPresent());
    }

    @Test
    void businessThrows_doesNotWriteFlag_reraises() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        Method m = Target.class.getMethod("reply", params);
        when(sig.getMethod()).thenReturn(m);
        when(sig.getReturnType()).thenReturn(Result.class);
        when(pjp.getArgs()).thenReturn(new Object[]{42L, 7L, reqWith("r1")});
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.ACQUIRED);
        doThrow(new IllegalStateException("boom")).when(pjp).proceed();

        assertThrows(IllegalStateException.class, () -> aspect.around(pjp, annotationOf("reply", params)));

        assertFalse(flagStore.isSuccess(FLAG_REPLY));
        verify(lockSupport).unlock(anyString());
    }

    @Test
    void blankKey_skipsIdempotency_andExecutes() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("")}, Result.class, new Result("ok"));

        Object out = aspect.around(pjp, annotationOf("reply", params));

        assertEquals("ok", ((Result) out).text);
        verify(lockSupport, never()).tryLock(anyString());
        assertFalse(flagStore.isSuccess(FLAG_REPLY));
    }
}
