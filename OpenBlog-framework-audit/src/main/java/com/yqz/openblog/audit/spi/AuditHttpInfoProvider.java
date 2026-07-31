package com.yqz.openblog.audit.spi;

import com.yqz.openblog.audit.model.AuditHttpInfo;

/**
 * 提供 HTTP 维度信息（IP/UA/路径等）。非 Web 场景可返回 null。
 */
public interface AuditHttpInfoProvider {
    AuditHttpInfo currentHttp();
}
