package com.yqz.openblog.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.notification.outbox.NotificationOutbox;
import com.yqz.openblog.notification.outbox.NotificationOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 通知门面：调用方只描述「发什么」（channel / recipient / subject / templateCode / params），
 * 由本类按渠道路由分发，渠道差异对调用方完全透明。
 * <p>
 * 双路径：
 * - {@link #submit(NotificationMessage)}：同步投递（强反馈场景，如注册验证码，Dubbo 直发）。
 * - {@link #submitAsync(NotificationMessage)}：异步投递（本地消息表 outbox → MQ → Consumer 投递）。
 * 两条路径共用同一套校验 / Channel 策略 / 幂等键，只是触发方式不同。
 */
@Service
public class NotificationService {

    private final ChannelRegistry registry;
    private final NotificationProperties properties;
    private final NotificationOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public NotificationService(ChannelRegistry registry,
                               NotificationProperties properties,
                               NotificationOutboxMapper outboxMapper,
                               ObjectMapper objectMapper) {
        this.registry = registry;
        this.properties = properties;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    /** 同步提交：校验 → 补默认主题 → 按渠道分发。失败抛 BizException（由调用方决定是否回滚）。 */
    public void submit(NotificationMessage message) {
        prepare(message);
        registry.resolve(message.getChannel()).send(message);
    }

    /**
     * 异步提交：与业务动作同事务写入 outbox（PENDING），由 Relay 发布 MQ、Consumer 投递。
     * 保证「业务提交」与「通知入队」原子，消息不丢失；messageId 贯穿 MQ 重投，不重复。
     */
    @Transactional
    public void submitAsync(NotificationMessage message) {
        if (!properties.getOutbox().isEnabled()) {
            throw new BizException(4000, "异步通知未启用");
        }
        prepare(message);

        // 一次 submitAsync = 一个 messageId：贯穿 outbox / MQ / 消费端幂等。
        if (message.getMessageId() == null || message.getMessageId().isBlank()) {
            message.setMessageId(UUID.randomUUID().toString());
        }

        try {
            String paramsJson = objectMapper.writeValueAsString(message.getParams());
            outboxMapper.insert(NotificationOutbox.from(message, paramsJson));
        } catch (JsonProcessingException e) {
            throw new BizException(4000, "通知参数序列化失败");
        }
    }

    /** 公共前置：非空校验 + 补默认主题。 */
    private void prepare(NotificationMessage message) {
        if (message == null) {
            throw new BizException(4000, "通知消息不能为空");
        }
        if (message.getChannel() == null) {
            throw new BizException(4000, "通知渠道不能为空");
        }
        if (message.getRecipient() == null || message.getRecipient().isBlank()) {
            throw new BizException(4000, "通知接收方不能为空");
        }
        if (message.getSubject() == null || message.getSubject().isBlank()) {
            message.setSubject(properties.getEmail().getDefaultSubject());
        }
    }
}
