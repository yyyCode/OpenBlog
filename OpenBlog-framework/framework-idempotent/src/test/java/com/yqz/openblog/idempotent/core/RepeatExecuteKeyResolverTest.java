package com.yqz.openblog.idempotent.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepeatExecuteKeyResolverTest {

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
        public void create(Long articleId, Long uid, TestReq req) {
        }
    }

    @Test
    void resolvesSpelKeysFromMethodArgs() throws Exception {
        Method method = Target.class.getMethod("create", Long.class, Long.class, TestReq.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);

        TestReq req = new TestReq();
        req.setRequestId("req-123");
        when(pjp.getArgs()).thenReturn(new Object[]{42L, 7L, req});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        List<String> keys = resolver.resolve(pjp, new String[]{"#articleId", "#uid", "#req.requestId"});

        assertEquals(List.of("42", "7", "req-123"), keys);
    }

    @Test
    void blankExpressionsAreSkipped() throws Exception {
        Method method = Target.class.getMethod("create", Long.class, Long.class, TestReq.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[]{42L, 7L, new TestReq()});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        List<String> keys = resolver.resolve(pjp, new String[]{"", "#uid"});

        assertEquals(List.of("7"), keys);
    }

    @Test
    void nullIntermediateProperty_skipsThatKey() throws Exception {
        Method method = Target.class.getMethod("create", Long.class, Long.class, TestReq.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[]{42L, 7L, null});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        List<String> keys = resolver.resolve(pjp, new String[]{"#req.requestId", "#uid"});

        assertEquals(List.of("7"), keys);
    }

    @Test
    void nullOrEmptyKeysArray_returnsEmptyList() throws Exception {
        Method method = Target.class.getMethod("create", Long.class, Long.class, TestReq.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[]{42L, 7L, new TestReq()});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        assertEquals(List.of(), resolver.resolve(pjp, null));
        assertEquals(List.of(), resolver.resolve(pjp, new String[]{}));
    }

    @Test
    void nullPropertyValue_resolvesToEmptyString() throws Exception {
        Method method = Target.class.getMethod("create", Long.class, Long.class, TestReq.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[]{42L, 7L, new TestReq()});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        List<String> keys = resolver.resolve(pjp, new String[]{"#req.requestId"});

        assertEquals(List.of(""), keys);
    }
}
