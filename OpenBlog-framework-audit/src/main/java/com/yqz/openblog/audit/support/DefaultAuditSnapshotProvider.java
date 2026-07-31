package com.yqz.openblog.audit.support;

import com.yqz.openblog.audit.model.AuditInvocation;
import com.yqz.openblog.audit.spi.AuditSnapshotProvider;

/**
 * 默认快照策略：
 * - before: 不采集
 * - after: 优先用返回值，返回值为空则不采集
 */
public class DefaultAuditSnapshotProvider implements AuditSnapshotProvider {
    @Override
    public Object before(AuditInvocation invocation) {
        return null;
    }

    @Override
    public Object after(AuditInvocation invocation) {
        return invocation.getResult();
    }
}
