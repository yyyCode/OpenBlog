package com.yqz.openblog.audit.model;

public record AuditHttpInfo(
        String method,
        String path,
        String ip,
        String userAgent
) {
}
