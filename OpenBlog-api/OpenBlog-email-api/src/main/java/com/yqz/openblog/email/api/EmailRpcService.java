package com.yqz.openblog.email.api;

/**
 * Dubbo RPC 邮件发送接口。
 */
public interface EmailRpcService {

    /**
     * 发送单封邮件，同步返回发送记录。
     */
    EmailSendResult send(EmailSendRequest request);
}
