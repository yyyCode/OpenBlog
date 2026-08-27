package com.yqz.openblog.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yqz.openblog.message.api.EmailSendRequest;
import com.yqz.openblog.message.api.EmailSendResult;
import com.yqz.openblog.message.dto.EmailRecordResponse;
import com.yqz.openblog.message.entity.EmailRecord;
import com.yqz.openblog.message.entity.EmailStatus;
import com.yqz.openblog.message.exception.EmailSendException;
import com.yqz.openblog.message.repo.EmailRecordMapper;
import com.yqz.openblog.message.sender.DirectMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
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
     * <p>
     * 幂等设计：调用方可携带 idempotencyKey（如 UUID）。同一幂等键的请求——
     * 无论被 Dubbo 消费端重试、网络重放还是并发重提——都只实际发送一次：
     * 命中已有记录时直接复用返回，不再调用发信通道。唯一索引 uk_idempotency_key
     * 是硬兜底，保证并发双插时也只有一个能真正发送。
     */
    public EmailSendResult send(EmailSendRequest req) {
        String key = req.getIdempotencyKey();
        if (key != null && !key.isBlank()) {
            EmailRecord existing = emailRecordMapper.selectOne(
                    Wrappers.lambdaQuery(EmailRecord.class).eq(EmailRecord::getIdempotencyKey, key));
            if (existing != null) {
                log.info("Idempotent replay: reuse recordId={}, key={}, status={}",
                        existing.getId(), key, existing.getStatus());
                return toResult(existing);
            }
        }
        return doSend(req, key);
    }

    private EmailSendResult doSend(EmailSendRequest req, String idempotencyKey) {
        // 1. 先写 PENDING 记录
        EmailRecord record = new EmailRecord();
        record.setRecipient(req.getRecipient());
        record.setSubject(req.getSubject());
        record.setBody(req.getBody());
        record.setStatus(EmailStatus.PENDING);
        record.setIdempotencyKey(idempotencyKey);
        try {
            emailRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发双插撞唯一索引：另一执行者已写入。查回其记录返回，不重复发送。
            EmailRecord existing = emailRecordMapper.selectOne(
                    Wrappers.lambdaQuery(EmailRecord.class).eq(EmailRecord::getIdempotencyKey, idempotencyKey));
            if (existing != null) {
                log.info("Idempotent race: reuse recordId={}, key={}, status={}",
                        existing.getId(), idempotencyKey, existing.getStatus());
                return toResult(existing);
            }
            throw e;
        }

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

        return toResult(record);
    }

    private EmailSendResult toResult(EmailRecord r) {
        return new EmailSendResult(
                r.getId(),
                r.getRecipient(),
                r.getSubject(),
                r.getStatus().name(),
                r.getErrorMsg(),
                r.getRequestId(),
                r.getSentAt(),
                r.getCreatedAt()
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
