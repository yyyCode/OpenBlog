package com.yqz.openblog.notification.mq;

import com.yqz.openblog.notification.NotificationMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 通知消息发布。由 {@code OutboxRelay} 扫描 outbox 后调用；发送成功才由 Relay 推进状态。
 * 发送失败抛异常，outbox 保持 PENDING，下轮重扫（至少一次）。
 */
@Component
public class NotificationMqProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public NotificationMqProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /** 发布一条通知消息（JSON 序列化）。失败抛异常，由调用方决定是否重扫。 */
    public void publish(NotificationMessage message) {
        rocketMQTemplate.convertAndSend(NotificationTopics.TOPIC, message);
    }
}
