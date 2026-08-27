package com.yqz.openblog.notification.channel;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.message.api.EmailRpcService;
import com.yqz.openblog.message.api.EmailSendRequest;
import com.yqz.openblog.message.api.EmailSendResult;
import com.yqz.openblog.notification.AbstractNotificationChannel;
import com.yqz.openblog.notification.NotificationChannelType;
import com.yqz.openblog.notification.NotificationMessage;
import com.yqz.openblog.notification.NotificationTemplateService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 邮件通知渠道（本期唯一策略）。继承模板方法基类，只实现投递差异：
 * 经 Dubbo 调 email 模块直发，复用已上线的幂等保障（retries=0 + 幂等键 + DB 唯一索引）。
 * <p>
 * 新增渠道（SMS / 飞书）参照本类再写一个 {@code extends AbstractNotificationChannel} 即可。
 */
@Component
@ConditionalOnProperty(prefix = "openblog.notification.email", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmailNotificationChannel extends AbstractNotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    /**
     * Dubbo 消费 email 服务。@DubboReference 需字段注入（Dubbo Spring Boot 3.x 对构造器注入支持不稳），
     * 沿用项目约定。retries=0：发送邮件是【非幂等】操作，必须关闭 Dubbo 默认重试，否则调用超时时
     * 同一封邮件会被发多次；timeout=5000ms 放宽默认的 1000ms（阿里云真实发送通常超过 1s）。
     */
    @DubboReference(retries = 0, timeout = 5000)
    private EmailRpcService emailRpcService;

    public EmailNotificationChannel(NotificationTemplateService templateService) {
        super(templateService);
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    protected void doSend(NotificationMessage message, String content) {
        // 幂等键：异步链路优先用 messageId（MQ 重投同一消息时，email 服务命中已有记录直接返回，
        // 不再重复发送）；同步链路 messageId 为空则生成 UUID。见 EmailService.send 幂等逻辑。
        String idempotencyKey = (message.getMessageId() == null || message.getMessageId().isBlank())
                ? UUID.randomUUID().toString()
                : message.getMessageId();
        EmailSendRequest request = new EmailSendRequest(message.getRecipient(), message.getSubject(), content);
        request.setIdempotencyKey(idempotencyKey);

        EmailSendResult result;
        try {
            result = emailRpcService.send(request);
        } catch (Exception e) {
            // No provider / RPC 异常 → 统一抛 5002，由调用方（EmailCodeService）决定是否回滚 Redis。
            log.warn("邮件通知发送失败（Dubbo 调用异常）。recipient={}", message.getRecipient(), e);
            throw new BizException(5002, "邮件服务暂不可用，请稍后再试");
        }

        if (!"SENT".equals(result.getStatus())) {
            log.warn("邮件通知发送失败（status={}, errorMsg={}）。recipient={}",
                    result.getStatus(), result.getErrorMsg(), message.getRecipient());
            throw new BizException(5002, "邮件发送失败，请稍后再试");
        }
    }
}
