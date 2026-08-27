package com.yqz.openblog.message.sender;

/**
 * 邮件发送抽象接口。
 */
public interface DirectMailSender {

    /**
     * 发送单封邮件。
     *
     * @param toAddress 收件人地址
     * @param subject   邮件主题
     * @param htmlBody  HTML 正文
     * @return 阿里云请求 ID
     */
    String send(String toAddress, String subject, String htmlBody);
}
