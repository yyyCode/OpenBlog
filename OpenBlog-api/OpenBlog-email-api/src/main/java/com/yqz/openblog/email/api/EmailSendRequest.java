package com.yqz.openblog.email.api;

import java.io.Serializable;

public class EmailSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String recipient;
    private String subject;
    private String body;

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
}
