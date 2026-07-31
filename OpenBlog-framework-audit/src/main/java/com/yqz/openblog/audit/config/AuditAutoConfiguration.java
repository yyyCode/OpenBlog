package com.yqz.openblog.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.audit.spi.AuditChannel;
import com.yqz.openblog.audit.spi.AuditHttpInfoProvider;
import com.yqz.openblog.audit.spi.AuditRecordHandler;
import com.yqz.openblog.audit.spi.AuditSnapshotProvider;
import com.yqz.openblog.audit.spi.AuditUserProvider;
import com.yqz.openblog.audit.aop.AuditLogAspect;
import com.yqz.openblog.audit.channel.LocalAuditChannel;
import com.yqz.openblog.audit.support.DefaultAuditHttpInfoProvider;
import com.yqz.openblog.audit.support.DefaultAuditSnapshotProvider;
import com.yqz.openblog.audit.support.DefaultAuditUserProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(prefix = "audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TaskExecutor auditTaskExecutor() {
        return new SimpleAsyncTaskExecutor("audit-");
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditUserProvider auditUserProvider() {
        return new DefaultAuditUserProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    public AuditHttpInfoProvider auditHttpInfoProvider() {
        return new DefaultAuditHttpInfoProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditSnapshotProvider auditSnapshotProvider() {
        return new DefaultAuditSnapshotProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditChannel auditChannel(
            AuditProperties props,
            TaskExecutor auditTaskExecutor,
            List<AuditRecordHandler> handlers
    ) {
        // 先落一个通用默认：local。Kafka 通道会在后续配置类里按条件覆盖。
        return new LocalAuditChannel(auditTaskExecutor, props.isAsync(), handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogAspect auditLogAspect(
            AuditUserProvider userProvider,
            AuditHttpInfoProvider httpInfoProvider,
            AuditSnapshotProvider snapshotProvider,
            AuditChannel channel,
            ObjectMapper objectMapper
    ) {
        return new AuditLogAspect(userProvider, httpInfoProvider, snapshotProvider, channel, objectMapper);
    }
}
