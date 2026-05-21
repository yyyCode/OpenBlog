package com.yqz.openblog.feedback.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "feedback_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_feedback_ip_day", columnNames = {"ip_key", "submit_day"})
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

