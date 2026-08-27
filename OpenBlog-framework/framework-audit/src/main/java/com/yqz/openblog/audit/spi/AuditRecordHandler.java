package com.yqz.openblog.audit.spi;

import com.yqz.openblog.audit.model.AuditRecord;

/**
 * 落库/归档/索引等处理器。
 * 企业可插拔实现：写 DB、发 ES、写文件等。
 */
public interface AuditRecordHandler {
    void handle(AuditRecord record);
}
