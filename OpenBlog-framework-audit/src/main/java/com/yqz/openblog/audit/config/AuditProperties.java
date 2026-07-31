package com.yqz.openblog.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "audit")
public class AuditProperties {
    /**
     * 是否开启审计。
     */
    private boolean enabled = true;

    /**
     * 是否异步处理（建议开启）。
     */
    private boolean async = true;

    /**
     * 传输通道：local / kafka
     */
    private String channel = "local";

    private final Kafka kafka = new Kafka();

    @Data
    public static class Kafka {
        /**
         * topic 名。
         */
        private String topic = "audit-record";

        /**
         * 是否启用 consumer（消费并交给 handler 持久化）。
         */
        private boolean consumerEnabled = false;
    }
}
