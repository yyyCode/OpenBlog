package com.yqz.openblog.message.api;

import java.io.Serializable;
import java.time.Instant;

public class EmailSendResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long recordId;
    private String recipient;
    private String subject;
    private String status;
    private String errorMsg;
    private String requestId;
    private Instant sentAt;
    private Instant createdAt;

    public EmailSendResult() {}

    public EmailSendResult(Long recordId, String recipient, String subject, String status,
                           String errorMsg, String requestId, Instant sentAt, Instant createdAt) {
        this.recordId = recordId;
        this.recipient = recipient;
        this.subject = subject;
        this.status = status;
        this.errorMsg = errorMsg;
        this.requestId = requestId;
        this.sentAt = sentAt;
        this.createdAt = createdAt;
    }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
