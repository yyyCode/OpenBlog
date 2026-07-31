package com.yqz.openblog.email.sender;

import com.aliyun.dm20151123.Client;
import com.aliyun.dm20151123.models.SingleSendMailRequest;
import com.aliyun.dm20151123.models.SingleSendMailResponse;
import com.aliyun.teaopenapi.models.Config;
import com.yqz.openblog.email.config.EmailProperties;
import com.yqz.openblog.email.exception.EmailSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class AliyunDirectMailSender implements DirectMailSender {

    private static final Logger log = LoggerFactory.getLogger(AliyunDirectMailSender.class);

    private final EmailProperties properties;
    private Client client;

    public AliyunDirectMailSender(EmailProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("Email sending is disabled (openblog.email.aliyun.enabled=false)");
            return;
        }
        try {
            Config config = new Config()
                    .setAccessKeyId(properties.getAccessKeyId())
                    .setAccessKeySecret(properties.getAccessKeySecret())
                    .setEndpoint("dm.aliyuncs.com");
            this.client = new Client(config);
            log.info("AliyunDirectMailSender initialized, from={}", properties.getFromAddress());
        } catch (Exception e) {
            log.error("Failed to init AliyunDirectMailSender: {}", e.getMessage());
        }
    }

    @Override
    public String send(String toAddress, String subject, String htmlBody) {
        if (!properties.isEnabled()) {
            log.info("[EMAIL-DISABLED] to={}, subject={}", toAddress, subject);
            return "disabled";
        }
        if (client == null) {
            throw new EmailSendException("DirectMail client not initialized — check AK/SK configuration");
        }

        try {
            SingleSendMailRequest request = new SingleSendMailRequest()
                    .setAccountName(properties.getFromAddress())
                    .setFromAlias(properties.getFromAlias())
                    .setAddressType(1)
                    .setReplyToAddress(false)
                    .setToAddress(toAddress)
                    .setSubject(subject)
                    .setHtmlBody(htmlBody);

            SingleSendMailResponse resp = client.singleSendMail(request);
            String requestId = resp.getBody() != null ? resp.getBody().getRequestId() : null;
            log.info("[EMAIL-SENT] to={}, subject={}, requestId={}", toAddress, subject, requestId);
            return requestId;
        } catch (Exception e) {
            log.error("[EMAIL-FAILED] to={}, subject={}: {}", toAddress, subject, e.getMessage());
            throw new EmailSendException("邮件发送失败: " + e.getMessage(), e);
        }
    }
}
