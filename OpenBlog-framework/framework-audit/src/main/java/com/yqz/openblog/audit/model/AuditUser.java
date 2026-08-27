package com.yqz.openblog.audit.model;

public record AuditUser(
        String userId,
        String username
) {
}
