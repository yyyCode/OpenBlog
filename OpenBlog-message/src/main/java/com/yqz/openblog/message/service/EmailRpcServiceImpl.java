package com.yqz.openblog.message.service;

import com.yqz.openblog.message.api.EmailRpcService;
import com.yqz.openblog.message.api.EmailSendRequest;
import com.yqz.openblog.message.api.EmailSendResult;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class EmailRpcServiceImpl implements EmailRpcService {

    private final EmailService emailService;

    public EmailRpcServiceImpl(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public EmailSendResult send(EmailSendRequest request) {
        return emailService.send(request);
    }
}
