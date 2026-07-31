package com.yqz.openblog.email.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yqz.openblog.email.api.EmailSendRequest;
import com.yqz.openblog.email.api.EmailSendResult;
import com.yqz.openblog.email.dto.EmailRecordResponse;
import com.yqz.openblog.email.service.EmailService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/email")
@CrossOrigin(origins = "*")
public class EmailAdminController {

    private final EmailService emailService;

    public EmailAdminController(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * 快速测试发送邮件。
     */
    @PostMapping("/test")
    public EmailSendResult testSend(@RequestBody Map<String, String> body) {
        EmailSendRequest req = new EmailSendRequest(
                body.get("recipient"),
                body.get("subject"),
                body.get("body")
        );
        return emailService.send(req);
    }

    @GetMapping("/records")
    public Map<String, Object> listRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        IPage<EmailRecordResponse> p = emailService.listRecords(page, size, status);
        return Map.of(
                "items", p.getRecords(),
                "total", p.getTotal(),
                "page", page,
                "size", size
        );
    }
}
