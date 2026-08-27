package com.yqz.openblog.audit.support;

import com.yqz.openblog.audit.model.AuditUser;
import com.yqz.openblog.audit.spi.AuditUserProvider;

/**
 * 默认用户提供器：未接入安全框架时返回匿名。
 * 业务方可在应用里定义同类型 Bean 覆盖。
 */
public class DefaultAuditUserProvider implements AuditUserProvider {
    @Override
    public AuditUser currentUser() {
        return new AuditUser("anonymous", "anonymous");
    }
}
