package com.yqz.openblog.audit.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.audit.annotation.AuditLog;
import com.yqz.openblog.audit.model.AuditInvocation;
import com.yqz.openblog.audit.model.AuditRecord;
import com.yqz.openblog.audit.spi.AuditChannel;
import com.yqz.openblog.audit.spi.AuditHttpInfoProvider;
import com.yqz.openblog.audit.spi.AuditSnapshotProvider;
import com.yqz.openblog.audit.spi.AuditUserProvider;
import com.yqz.openblog.audit.support.AuditJson;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Aspect
public class AuditLogAspect {
    private final AuditUserProvider userProvider;
    private final AuditHttpInfoProvider httpInfoProvider;
    private final AuditSnapshotProvider snapshotProvider;
    private final AuditChannel channel;
    private final AuditJson auditJson;

    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AuditLogAspect(
            AuditUserProvider userProvider,
            AuditHttpInfoProvider httpInfoProvider,
            AuditSnapshotProvider snapshotProvider,
            AuditChannel channel,
            ObjectMapper objectMapper
    ) {
        this.userProvider = userProvider;
        this.httpInfoProvider = httpInfoProvider;
        this.snapshotProvider = snapshotProvider;
        this.channel = channel;
        this.auditJson = new AuditJson(objectMapper);
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        Instant startedAt = Instant.now();
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();

        AuditInvocation inv = new AuditInvocation();
        inv.setAuditLog(auditLog);
        inv.setMethod(method);
        inv.setTarget(pjp.getTarget());
        inv.setArgs(pjp.getArgs());
        inv.setStartedAt(startedAt);

        String traceId = resolveTraceId();
        inv.setTraceId(traceId);
        inv.setUser(userProvider.currentUser());
        inv.setHttp(httpInfoProvider.currentHttp());
        inv.setSpelVars(buildSpelVars(method, pjp.getArgs()));

        try {
            inv.setBeforeSnapshot(snapshotProvider.before(inv));
        } catch (Exception ignored) {
            // before snapshot 失败不影响主流程
        }

        Object result = null;
        Throwable err = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable e) {
            err = e;
            throw e;
        } finally {
            inv.setResult(result);
            inv.setError(err);
            inv.setElapsedMs(Duration.between(startedAt, Instant.now()).toMillis());
            try {
                inv.setAfterSnapshot(snapshotProvider.after(inv));
            } catch (Exception ignored) {
                // after snapshot 失败不影响主流程
            }
            AuditRecord record = toRecord(inv);
            channel.publish(record);
        }
    }

    private AuditRecord toRecord(AuditInvocation inv) {
        AuditLog al = inv.getAuditLog();
        return new AuditRecord(
                inv.getTraceId(),
                inv.getUser(),
                inv.getHttp(),
                al.action(),
                al.entityType(),
                resolveEntityId(al.entityId(), inv.getSpelVars()),
                inv.getMethod().getDeclaringClass().getName() + "#" + inv.getMethod().getName(),
                inv.getError() == null,
                inv.getElapsedMs(),
                Instant.now(),
                auditJson.toJson(inv.getBeforeSnapshot()),
                auditJson.toJson(inv.getAfterSnapshot()),
                al.recordArgs() ? auditJson.toJson(inv.getArgs()) : null,
                al.recordResult() ? auditJson.toJson(inv.getResult()) : null,
                inv.getError() != null ? inv.getError().getClass().getName() + ": " + inv.getError().getMessage() : null
        );
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
        }
        return traceId;
    }

    private Map<String, Object> buildSpelVars(Method method, Object[] args) {
        Map<String, Object> vars = new HashMap<>();
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                vars.put(names[i], args[i]);
            }
        }
        // 兼容未开启 -parameters 的场景：提供 p0/a0 变量给 SpEL 与快照提取用
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                vars.put("p" + i, args[i]);
                vars.put("a" + i, args[i]);
            }
        }
        vars.put("args", args);
        return vars;
    }

    private String resolveEntityId(String expr, Map<String, Object> vars) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            for (Map.Entry<String, Object> e : vars.entrySet()) {
                ctx.setVariable(e.getKey(), e.getValue());
            }
            Expression expression = spelParser.parseExpression(expr);
            Object value = expression.getValue(ctx);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }
}
