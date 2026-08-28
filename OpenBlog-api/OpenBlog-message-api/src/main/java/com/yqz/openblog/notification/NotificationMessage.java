package com.yqz.openblog.notification;

import java.io.Serializable;
import java.util.Map;

/**
 * 统一通知消息模型。
 * <p>
 * 渠道无关：channel 决定由哪个策略投递，recipient/subject 为投递目标，templateCode + params
 * 经 {@link NotificationTemplateService} 渲染成各渠道内容。未来接 SMS/飞书时扩展 templateCode
 * 与模板即可，调用方无需感知渠道实现差异。
 */
public class NotificationMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全局幂等键（业务方生成，如 UUID）：贯穿 outbox / MQ / 消费端投递。
     * 异步链路下由 submitAsync 生成；通道据此传给下游（如 EmailSendRequest.idempotencyKey），
     * 保证同一条消息被 MQ 重投 N 次也只真正投递一次。同步链路可空（通道自行生成）。
     */
    private String messageId;
    private NotificationChannelType channel;
    private String recipient;
    private String subject;
    private String templateCode;
    private Map<String, Object> params;

    public NotificationMessage() {}

    public NotificationMessage(String messageId, NotificationChannelType channel, String recipient, String subject,
                               String templateCode, Map<String, Object> params) {
        this.messageId = messageId;
        this.channel = channel;
        this.recipient = recipient;
        this.subject = subject;
        this.templateCode = templateCode;
        this.params = params;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public NotificationChannelType getChannel() { return channel; }
    public void setChannel(NotificationChannelType channel) { this.channel = channel; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final NotificationMessage msg = new NotificationMessage();

        public Builder messageId(String messageId) { msg.messageId = messageId; return this; }
        public Builder channel(NotificationChannelType channel) { msg.channel = channel; return this; }
        public Builder recipient(String recipient) { msg.recipient = recipient; return this; }
        public Builder subject(String subject) { msg.subject = subject; return this; }
        public Builder templateCode(String templateCode) { msg.templateCode = templateCode; return this; }
        public Builder params(Map<String, Object> params) { msg.params = params; return this; }

        public NotificationMessage build() { return msg; }
    }
}
