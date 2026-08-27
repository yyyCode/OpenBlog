package com.yqz.openblog.message.api;

import java.io.Serializable;

public class EmailSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String recipient;
    private String subject;
    private String body;

    /**
     * 幂等键（业务方生成，如 UUID）。provider 据此去重：
     * 同一幂等键的请求无论被重试/重放多少次，只发送一次。
     * 可空 —— 兼容旧调用方（无幂等键时按原逻辑直接发送）。
     */
    private String idempotencyKey;

    public EmailSendRequest() {}

    public EmailSendRequest(String recipient, String subject, String body) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
