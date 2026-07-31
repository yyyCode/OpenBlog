package com.yqz.openblog.audit.spi;

import com.yqz.openblog.audit.model.AuditInvocation;

/**
 * 快照提取扩展点：支持企业按"实体/表/业务对象"获取前后状态。
 *
 * 默认实现通常只采集 after（如返回值/再次查询），before 可按需查询 DB。
 */
public interface AuditSnapshotProvider {
    /**
     * 执行业务方法前获取快照（可返回 null）。
     */
    Object before(AuditInvocation invocation);

    /**
     * 执行业务方法后获取快照（可返回 null）。
     */
    Object after(AuditInvocation invocation);
}
