package com.yqz.openblog.notification.channel;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.message.api.EmailSendRequest;
import com.yqz.openblog.message.api.EmailSendResult;
import com.yqz.openblog.message.api.NotificationRpcService;
import com.yqz.openblog.message.service.EmailService;
import com.yqz.openblog.notification.AbstractNotificationChannel;
import com.yqz.openblog.notification.NotificationChannelType;
import com.yqz.openblog.notification.NotificationMessage;
import com.yqz.openblog.notification.NotificationTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 邮件通知渠道（迁入 message 后的 in-process 版本）。
 * 继承模板方法基类，直接调 message 内 {@link EmailService} 直发，复用其幂等保障
 * （idempotencyKey + uk_idempotency_key 唯一索引）。新增渠道（SMS / 飞书）参照本类扩展。
 */
@Component
@ConditionalOnProperty(prefix = "openblog.notification.email", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmailNotificationChannel extends AbstractNotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final EmailService emailService;

    public EmailNotificationChannel(NotificationTemplateService templateService, EmailService emailService) {
        super(templateService);
        this.emailService = emailService;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    protected void doSend(NotificationMessage message, String content) {
        String idempotencyKey = (message.getMessageId() == null || message.getMessageId().isBlank())
                ? UUID.randomUUID().toString()
                : message.getMessageId();
        EmailSendRequest request = new EmailSendRequest(message.getRecipient(), message.getSubject(), content);
        request.setIdempotencyKey(idempotencyKey);

        EmailSendResult result;
        try {
            result = emailService.send(request);
        } catch (Exception e) {
            log.warn("邮件通知发送失败。recipient={}", message.getRecipient(), e);
            throw new BizException(NotificationRpcService.ERROR_CODE_EMAIL_UNAVAILABLE, "邮件服务暂不可用，请稍后再试");
        }

        if (!"SENT".equals(result.getStatus())) {
            log.warn("邮件通知发送失败（status={}, errorMsg={}）。recipient={}",
                    result.getStatus(), result.getErrorMsg(), message.getRecipient());
            throw new BizException(NotificationRpcService.ERROR_CODE_EMAIL_UNAVAILABLE, "邮件发送失败，请稍后再试");
        }
    }
}
