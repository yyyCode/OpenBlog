package com.yqz.openblog.audit.support;

import com.yqz.openblog.audit.entity.AuditRecordEntity;
import com.yqz.openblog.audit.model.AuditRecord;
import com.yqz.openblog.audit.repo.AuditRecordMapper;
import com.yqz.openblog.audit.spi.AuditRecordHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DbAuditRecordHandler implements AuditRecordHandler {

    private static final Logger log = LoggerFactory.getLogger(DbAuditRecordHandler.class);

    private final AuditRecordMapper auditRecordMapper;

    public DbAuditRecordHandler(AuditRecordMapper auditRecordMapper) {
        this.auditRecordMapper = auditRecordMapper;
    }

    @Override
    public void handle(AuditRecord record) {
        try {
            AuditRecordEntity entity = toEntity(record);
            auditRecordMapper.insert(entity);
        } catch (Exception e) {
            log.error("Failed to persist audit record: action={}, error={}", record.action(), e.getMessage());
        }
    }

    private AuditRecordEntity toEntity(AuditRecord r) {
        AuditRecordEntity e = new AuditRecordEntity();
        e.setTraceId(r.traceId());
        if (r.user() != null) {
            try { e.setUserId(Long.parseLong(r.user().userId())); } catch (NumberFormatException ignored) {}
            e.setUsername(r.user().username());
        }
        if (r.http() != null) {
            e.setClientIp(r.http().ip());
            e.setUserAgent(r.http().userAgent());
            e.setRequestUri(r.http().path());
            e.setHttpMethod(r.http().method());
        }
        e.setAction(r.action());
        e.setEntityType(r.entityType());
        e.setEntityId(r.entityId());
        e.setMethod(r.javaMethod());
        e.setSuccess(r.success());
        e.setElapsedMs(r.elapsedMs());
        e.setBeforeSnapshot(r.beforeJson());
        e.setAfterSnapshot(r.afterJson());
        e.setArgs(r.argsJson());
        e.setResult(r.resultJson());
        e.setErrorMsg(r.errorMsg());
        return e;
    }
}
