package com.yqz.openblog.email.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yqz.openblog.email.api.EmailSendRequest;
import com.yqz.openblog.email.api.EmailSendResult;
import com.yqz.openblog.email.dto.EmailRecordResponse;
import com.yqz.openblog.email.entity.EmailRecord;
import com.yqz.openblog.email.entity.EmailStatus;
import com.yqz.openblog.email.exception.EmailSendException;
import com.yqz.openblog.email.repo.EmailRecordMapper;
import com.yqz.openblog.email.sender.DirectMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailRecordMapper emailRecordMapper;
    private final DirectMailSender directMailSender;

    public EmailService(EmailRecordMapper emailRecordMapper, DirectMailSender directMailSender) {
        this.emailRecordMapper = emailRecordMapper;
        this.directMailSender = directMailSender;
    }

    /**
     * 发送邮件并记录到数据库。
     */
    public EmailSendResult send(EmailSendRequest req) {
        // 1. 先写 PENDING 记录
        EmailRecord record = new EmailRecord();
        record.setRecipient(req.getRecipient());
        record.setSubject(req.getSubject());
        record.setBody(req.getBody());
        record.setStatus(EmailStatus.PENDING);
        emailRecordMapper.insert(record);

        // 2. 发送邮件
        try {
            String requestId = directMailSender.send(req.getRecipient(), req.getSubject(), req.getBody());
            record.setStatus(EmailStatus.SENT);
            record.setRequestId(requestId);
            record.setSentAt(Instant.now());
        } catch (EmailSendException e) {
            record.setStatus(EmailStatus.FAILED);
            record.setErrorMsg(e.getMessage());
            log.warn("Email send failed: recordId={}, to={}", record.getId(), req.getRecipient());
        }
        emailRecordMapper.updateById(record);

        return new EmailSendResult(
                record.getId(),
                record.getRecipient(),
                record.getSubject(),
                record.getStatus().name(),
                record.getErrorMsg(),
                record.getRequestId(),
                record.getSentAt(),
                record.getCreatedAt()
        );
    }

    /**
     * 分页查询邮件记录（管理后台用）。
     */
    public IPage<EmailRecordResponse> listRecords(int page, int size, String statusFilter) {
        LambdaQueryWrapper<EmailRecord> w = Wrappers.lambdaQuery(EmailRecord.class)
                .orderByDesc(EmailRecord::getCreatedAt);
        if (statusFilter != null && !statusFilter.isBlank()) {
            w.eq(EmailRecord::getStatus, EmailStatus.valueOf(statusFilter));
        }

        IPage<EmailRecord> p = emailRecordMapper.selectPage(new Page<>(page + 1, size), w);

        List<EmailRecordResponse> items = p.getRecords().stream().map(r -> {
            EmailRecordResponse resp = new EmailRecordResponse();
            resp.setId(r.getId());
            resp.setRecipient(r.getRecipient());
            resp.setSubject(r.getSubject());
            resp.setStatus(r.getStatus().name());
            resp.setErrorMsg(r.getErrorMsg());
            resp.setRequestId(r.getRequestId());
            resp.setSentAt(r.getSentAt());
            resp.setCreatedAt(r.getCreatedAt());
            return resp;
        }).collect(Collectors.toList());

        IPage<EmailRecordResponse> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(items);
        return result;
    }
}
