package com.yqz.openblog.message.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.message.api.EmailSendRequest;
import com.yqz.openblog.message.api.EmailSendResult;
import com.yqz.openblog.message.dto.EmailRecordResponse;
import com.yqz.openblog.message.service.EmailService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/message")
@CrossOrigin(origins = "*")
public class EmailAdminController {

    private final EmailService emailService;

    public EmailAdminController(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * 快速测试发送邮件：POST /api/v1/message/test?recipient=xxx&subject=xxx&body=xxx
     */
    @PostMapping("/test")
    public ApiResponse<EmailSendResult> testSend(
            @RequestParam String recipient,
            @RequestParam String subject,
            @RequestParam String body) {
        EmailSendRequest req = new EmailSendRequest(recipient, subject, body);
        return ApiResponse.ok(emailService.send(req));
    }

    @GetMapping("/records")
    public ApiResponse<PageResult<EmailRecordResponse>> listRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        IPage<EmailRecordResponse> p = emailService.listRecords(page, size, status);
        PageResult<EmailRecordResponse> pr = new PageResult<>(p.getRecords(), page, size, p.getTotal());
        return ApiResponse.ok(pr);
    }
}
