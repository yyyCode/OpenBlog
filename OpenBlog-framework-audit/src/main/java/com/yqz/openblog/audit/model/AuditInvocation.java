package com.yqz.openblog.audit.model;

import com.yqz.openblog.audit.annotation.AuditLog;
import lombok.Data;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;

@Data
public class AuditInvocation {
    private AuditLog auditLog;
    private Method method;
    private Object target;
    private Object[] args;

    private Instant startedAt;
    private Long elapsedMs;

    private String traceId;
    private AuditUser user;
    private AuditHttpInfo http;

    private Map<String, Object> spelVars;

    private Object beforeSnapshot;
    private Object result;
    private Object afterSnapshot;
    private Throwable error;
}
