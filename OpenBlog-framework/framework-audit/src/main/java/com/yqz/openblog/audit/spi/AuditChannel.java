package com.yqz.openblog.audit.spi;

import com.yqz.openblog.audit.model.AuditRecord;

/**
 * 传输通道：本地直达 / Kafka / 其他 MQ。
 * audit 模块只定义抽象，具体实现由 starter 或业务方提供。
 */
public interface AuditChannel {
    void publish(AuditRecord record);
}
