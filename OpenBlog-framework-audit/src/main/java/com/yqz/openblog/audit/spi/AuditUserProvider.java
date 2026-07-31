package com.yqz.openblog.audit.spi;

import com.yqz.openblog.audit.model.AuditUser;

/**
 * 提供当前用户信息。企业可替换实现以对接 SSO/安全框架。
 */
public interface AuditUserProvider {
    AuditUser currentUser();
}
