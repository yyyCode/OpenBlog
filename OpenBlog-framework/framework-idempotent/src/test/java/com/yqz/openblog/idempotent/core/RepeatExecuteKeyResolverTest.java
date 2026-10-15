package com.yqz.openblog.idempotent.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private ProceedingJoinPoint pjpWith(Object[] args) throws Exception {
        Method method = Target.class.getMethod("create", Long.class, Long.class, TestReq.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(args);
        return pjp;
    }

    private TestReq reqWith(String requestId) {
        TestReq req = new TestReq();
        req.setRequestId(requestId);
        return req;
    }

    @Test
    void allKeysResolve_returnsValuesInOrder() throws Exception {
        ProceedingJoinPoint pjp = pjpWith(new Object[]{42L, 7L, reqWith("req-123")});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        Optional<List<String>> keys = resolver.resolve(pjp, new String[]{"#articleId", "#uid", "#req.requestId"});

        assertEquals(Optional.of(List.of("42", "7", "req-123")), keys);
    }

    @Test
    void nullIntermediateProperty_makesWholeKeySetUnusable() throws Exception {
        // req 为 null → #req.requestId 抛 EvaluationException。
        // 绝不能"丢掉这一项、只留 #uid"：那样 Key 会退化成 (articleId, uid)，
        // 把同一用户 30s 内的第二条正常评论误判成重复。
        ProceedingJoinPoint pjp = pjpWith(new Object[]{42L, 7L, null});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        Optional<List<String>> keys = resolver.resolve(pjp, new String[]{"#req.requestId", "#uid"});

        assertTrue(keys.isEmpty());
    }

    @Test
    void misspelledProperty_makesWholeKeySetUnusable() throws Exception {
        // 对象上的属性名写错（#req.artcleId）→ EvaluationException。
        // 绝不能"丢掉这一项、只留 #uid"：那样 Key 会静默退化成更粗的粒度，误伤正常请求。
        ProceedingJoinPoint pjp = pjpWith(new Object[]{42L, 7L, reqWith("req-123")});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        Optional<List<String>> keys = resolver.resolve(pjp, new String[]{"#req.artcleId", "#uid"});

        assertTrue(keys.isEmpty());
    }

    @Test
    void unknownVariable_makesWholeKeySetUnusable() throws Exception {
        // 变量名写错（#artcleId）：SpEL 解析为 null，同样必须整组作废
        ProceedingJoinPoint pjp = pjpWith(new Object[]{42L, 7L, reqWith("req-123")});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        Optional<List<String>> keys = resolver.resolve(pjp, new String[]{"#artcleId", "#uid"});

        assertTrue(keys.isEmpty());
    }

    @Test
    void malformedExpression_doesNotThrow_makesKeySetUnusable() throws Exception {
        // 语法错误抛的是 ParseException（不是 EvaluationException）：
        // 必须兜住，否则异常穿透切面 → 业务请求 500，违反幂等组件 fail-open 契约
        ProceedingJoinPoint pjp = pjpWith(new Object[]{42L, 7L, reqWith("req-123")});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        Optional<List<String>> keys = resolver.resolve(pjp, new String[]{"#articleId +", "#uid"});

        assertTrue(keys.isEmpty());
    }

    @Test
    void blankExpression_makesKeySetUnusable() throws Exception {
        ProceedingJoinPoint pjp = pjpWith(new Object[]{42L, 7L, reqWith("req-123")});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        Optional<List<String>> keys = resolver.resolve(pjp, new String[]{"", "#uid"});

        assertTrue(keys.isEmpty());
    }

    @Test
    void nullPropertyValue_makesKeySetUnusable() throws Exception {
        ProceedingJoinPoint pjp = pjpWith(new Object[]{42L, 7L, reqWith(null)});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        Optional<List<String>> keys = resolver.resolve(pjp, new String[]{"#req.requestId"});

        assertTrue(keys.isEmpty());
    }

    @Test
    void nullOrEmptyKeysArray_isUnusable() throws Exception {
        ProceedingJoinPoint pjp = pjpWith(new Object[]{42L, 7L, reqWith("req-123")});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        assertTrue(resolver.resolve(pjp, null).isEmpty());
        assertTrue(resolver.resolve(pjp, new String[]{}).isEmpty());
    }
}
