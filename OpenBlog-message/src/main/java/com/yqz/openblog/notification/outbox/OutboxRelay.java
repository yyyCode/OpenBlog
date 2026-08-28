package com.yqz.openblog.notification.outbox;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.notification.NotificationChannelType;
import com.yqz.openblog.notification.NotificationMessage;
import com.yqz.openblog.notification.NotificationProperties;
import com.yqz.openblog.notification.mq.NotificationMqProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * outbox 中继：定时扫描 PENDING 记录 → 发布到 MQ → 置 PUBLISHED。
 * <p>
 * <b>至少一次发布</b>：发布成功才推进状态；崩溃发生在「已发布未置状态」窗口时，重扫会再次发布，
 * 重复消息由消费端按 message_id 幂等兜底（不重复投递）。单实例部署，fixedDelay 串行执行；
 * 多实例部署时会重复发布，幂等兜底使其无害（对账见 outbox 状态）。
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final int BATCH_SIZE = 100;

    private final NotificationOutboxMapper outboxMapper;
    private final NotificationMqProducer producer;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;

    public OutboxRelay(NotificationOutboxMapper outboxMapper,
                       NotificationMqProducer producer,
                       NotificationProperties properties,
                       ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.producer = producer;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${openblog.notification.outbox.relay-interval-ms:5000}")
    public void relay() {
        // 跳过刚提交的记录，留业务事务提交窗口（避免读到未提交的脏窗口）。
        LocalDateTime cutoff = LocalDateTime.now()
                .minus(Duration.ofMillis(properties.getOutbox().getPublishWindowMs()));

        List<NotificationOutbox> pending = outboxMapper.selectList(Wrappers.lambdaQuery(NotificationOutbox.class)
                .eq(NotificationOutbox::getStatus, NotificationOutbox.STATUS_PENDING)
                .le(NotificationOutbox::getCreatedAt, cutoff)
                .orderByAsc(NotificationOutbox::getId)
                .last("LIMIT " + BATCH_SIZE));

        for (NotificationOutbox row : pending) {
            try {
                producer.publish(toMessage(row));
                outboxMapper.markPublished(row.getId());
            } catch (Exception e) {
                // 发布失败：留 PENDING，下轮重扫（至少一次）。last_error / retry_count 留痕便于排查。
                log.warn("outbox 发布失败 id={} messageId={} channel={}",
                        row.getId(), row.getMessageId(), row.getChannel(), e);
                row.setLastError(truncate(e.getMessage()));
                row.setRetryCount((row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1);
                outboxMapper.updateById(row);
            }
        }
    }

    /** 由 outbox 行重建 NotificationMessage（MQ 载荷）。 */
    private NotificationMessage toMessage(NotificationOutbox row) {
        NotificationMessage message = new NotificationMessage();
        message.setMessageId(row.getMessageId());
        message.setChannel(NotificationChannelType.valueOf(row.getChannel()));
        message.setRecipient(row.getRecipient());
        message.setSubject(row.getSubject());
        message.setTemplateCode(row.getTemplateCode());
        message.setParams(deserializeParams(row.getParamsJson()));
        return message;
    }

    private Map<String, Object> deserializeParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(paramsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("outbox 参数反序列化失败，按空参数处理。paramsJson={}", paramsJson, e);
            return Map.of();
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
