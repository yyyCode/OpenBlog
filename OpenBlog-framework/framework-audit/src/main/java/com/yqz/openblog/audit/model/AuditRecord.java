package com.yqz.openblog.audit.model;

import java.time.Instant;

public record AuditRecord(
        String traceId,
        AuditUser user,
        AuditHttpInfo http,
        String action,
        String entityType,
        String entityId,
        String javaMethod,
        Boolean success,
        Long elapsedMs,
        Instant occurredAt,
        String beforeJson,
        String afterJson,
        String argsJson,
        String resultJson,
        String errorMsg
) {
}
