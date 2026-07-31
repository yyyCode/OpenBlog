package com.yqz.openblog.audit.channel;

import com.yqz.openblog.audit.model.AuditRecord;
import com.yqz.openblog.audit.spi.AuditChannel;
import com.yqz.openblog.audit.spi.AuditRecordHandler;
import org.springframework.core.task.TaskExecutor;

import java.util.List;

public class LocalAuditChannel implements AuditChannel {
    private final TaskExecutor executor;
    private final boolean async;
    private final List<AuditRecordHandler> handlers;

    public LocalAuditChannel(TaskExecutor executor, boolean async, List<AuditRecordHandler> handlers) {
        this.executor = executor;
        this.async = async;
        this.handlers = handlers;
    }

    @Override
    public void publish(AuditRecord record) {
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        Runnable job = () -> {
            for (AuditRecordHandler handler : handlers) {
                handler.handle(record);
            }
        };
        if (async) {
            executor.execute(job);
        } else {
            job.run();
        }
    }
}
