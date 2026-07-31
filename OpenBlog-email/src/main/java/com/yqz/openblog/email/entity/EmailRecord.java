package com.yqz.openblog.email.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("email_records")
public class EmailRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("recipient")
    private String recipient;

    @TableField("subject")
    private String subject;

    @TableField("body")
    private String body;

    @TableField("status")
    private EmailStatus status = EmailStatus.PENDING;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("request_id")
    private String requestId;

    @TableField("sent_at")
    private Instant sentAt;

    @TableField("created_at")
    private Instant createdAt = Instant.now();

    public EmailRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public EmailStatus getStatus() { return status; }
    public void setStatus(EmailStatus status) { this.status = status; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
