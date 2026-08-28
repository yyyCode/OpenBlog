package com.yqz.openblog.notification.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yqz.openblog.notification.NotificationMessage;

import java.time.LocalDateTime;

/**
 * 通知本地消息表（Transactional Outbox）实体。
 * <p>
 * 待投递任务：submitAsync 与业务动作同事务写入 PENDING → Relay 发布 MQ 置 PUBLISHED →
 * Consumer 投递成功置 SENT。message_id 唯一，贯穿 outbox / MQ / 消费端幂等。
 */
@TableName("notification_outbox")
public class NotificationOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_SENT = "SENT";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String channel;
    private String recipient;
    private String subject;
    private String templateCode;
    private String paramsJson;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 由 NotificationMessage 构造待投递记录（status=PENDING, retryCount=0）。 */
    public static NotificationOutbox from(NotificationMessage m, String paramsJson) {
        NotificationOutbox o = new NotificationOutbox();
        o.setMessageId(m.getMessageId());
        o.setChannel(m.getChannel().name());
        o.setRecipient(m.getRecipient());
        o.setSubject(m.getSubject());
        o.setTemplateCode(m.getTemplateCode());
        o.setParamsJson(paramsJson);
        o.setStatus(STATUS_PENDING);
        o.setRetryCount(0);
        return o;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
