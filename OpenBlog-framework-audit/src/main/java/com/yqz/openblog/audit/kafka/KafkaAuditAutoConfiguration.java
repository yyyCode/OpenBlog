package com.yqz.openblog.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.audit.model.AuditRecord;
import com.yqz.openblog.audit.spi.AuditChannel;
import com.yqz.openblog.audit.spi.AuditRecordHandler;
import com.yqz.openblog.audit.config.AuditProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "audit", name = "channel", havingValue = "kafka")
public class KafkaAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditChannel kafkaAuditChannel(
            AuditProperties props,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        return new KafkaAuditChannel(props.getKafka().getTopic(), kafkaTemplate, objectMapper);
    }

    @Bean
    public KafkaAuditConsumer kafkaAuditConsumer(
            AuditProperties props,
            ObjectMapper objectMapper,
            List<AuditRecordHandler> handlers
    ) {
        return new KafkaAuditConsumer(props, objectMapper, handlers);
    }

    public static class KafkaAuditConsumer {
        private final AuditProperties props;
        private final ObjectMapper objectMapper;
        private final List<AuditRecordHandler> handlers;

        public KafkaAuditConsumer(AuditProperties props, ObjectMapper objectMapper, List<AuditRecordHandler> handlers) {
            this.props = props;
            this.objectMapper = objectMapper;
            this.handlers = handlers;
        }

        @KafkaListener(
                topics = "#{@auditProperties.kafka.topic}",
                groupId = "audit-consumer",
                autoStartup = "#{@auditProperties.kafka.consumerEnabled}"
        )
        public void onMessage(String msg) throws Exception {
            if (handlers == null || handlers.isEmpty()) {
                return;
            }
            AuditRecord record = objectMapper.readValue(msg, AuditRecord.class);
            for (AuditRecordHandler handler : handlers) {
                handler.handle(record);
            }
        }
    }
}
