package com.yqz.openblog.feedback.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "feedback_entries", uniqueConstraints = {
        // 限额维度：同一登录用户每天一次（ip_key 已降级为审计字段，不再参与唯一性）
        @UniqueConstraint(name = "uk_feedback_user_day", columnNames = {"user_id", "submit_day"})
}, indexes = {
        @Index(name = "idx_feedback_created_at", columnList = "created_at"),
        @Index(name = "idx_feedback_submit_day", columnList = "submit_day")
})
public class FeedbackEntry {

    public enum Status {
        PENDING,
        RESOLVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 提交用户 ID：迁移前的历史行为 NULL（当时无归属用户） */
    @Column(name = "user_id")
    private Long userId;

    /** 提交来源 IP 摘要：仅作审计/风控，不参与唯一性判定 */
    @Column(name = "ip_key", nullable = false, length = 80)
    private String ipKey;

    @Column(name = "submitter_name", nullable = false, length = 50)
    private String submitterName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "submit_day", nullable = false)
    private LocalDate submitDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = Status.PENDING;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getIpKey() {
        return ipKey;
    }

    public void setIpKey(String ipKey) {
        this.ipKey = ipKey;
    }

    public String getSubmitterName() {
        return submitterName;
    }

    public void setSubmitterName(String submitterName) {
        this.submitterName = submitterName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDate getSubmitDay() {
        return submitDay;
    }

    public void setSubmitDay(LocalDate submitDay) {
        this.submitDay = submitDay;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

