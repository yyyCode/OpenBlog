package com.yqz.openblog.notification.mq;

import com.yqz.openblog.notification.NotificationMessage;
import com.yqz.openblog.notification.NotificationService;
import com.yqz.openblog.notification.outbox.NotificationOutboxMapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 通知消息消费者：收到 MQ 消息 → 复用同步门面投递（渲染 + 路由 + Channel 策略）→ 成功置 outbox SENT。
 * <p>
 * 失败抛出：交由 RocketMQ 按消费组退避重投，重投仍携带同一 messageId → 通道幂等，不重复投递；
 * 超过重试上限进 RocketMQ DLQ（死信）留待人工 / 告警（P2 再细化 FAILED/DEAD 状态机）。
 * 重投期间 outbox 保持 PUBLISHED，与「已发布未送达」的实际状态一致。
 */
@ConditionalOnProperty(prefix = "openblog.notification.mq", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
@RocketMQMessageListener(
        topic = NotificationTopics.TOPIC,
        consumerGroup = NotificationTopics.CONSUMER_GROUP)
public class NotificationMqConsumer implements RocketMQListener<NotificationMessage> {

    private static final Logger log = LoggerFactory.getLogger(NotificationMqConsumer.class);

    private final NotificationService notificationService;
    private final NotificationOutboxMapper outboxMapper;

    public NotificationMqConsumer(NotificationService notificationService,
                                  NotificationOutboxMapper outboxMapper) {
        this.notificationService = notificationService;
        this.outboxMapper = outboxMapper;
    }

    @Override
    public void onMessage(NotificationMessage message) {
        try {
            notificationService.submit(message);
            outboxMapper.markSent(message.getMessageId());
        } catch (Exception e) {
            // 永久性失败（如模板未知、渠道未配置）也会重投；超上限进 DLQ 后再人工处理。
            log.warn("MQ 通知投递失败 messageId={} channel={} recipient={}",
                    message.getMessageId(), message.getChannel(), message.getRecipient(), e);
            throw e;
        }
    }
}
