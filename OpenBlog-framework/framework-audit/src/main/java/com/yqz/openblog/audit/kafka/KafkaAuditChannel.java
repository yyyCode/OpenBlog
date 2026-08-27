package com.yqz.openblog.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.audit.model.AuditRecord;
import com.yqz.openblog.audit.spi.AuditChannel;

import org.springframework.kafka.core.KafkaTemplate;

public class KafkaAuditChannel implements AuditChannel {
    private final String topic;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaAuditChannel(String topic, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.topic = topic;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(AuditRecord record) {
        try {
            String json = objectMapper.writeValueAsString(record);
            kafkaTemplate.send(topic, record.traceId(), json);
        } catch (Exception e) {
            // MQ 不可用时，不影响主业务
        }
    }
}
