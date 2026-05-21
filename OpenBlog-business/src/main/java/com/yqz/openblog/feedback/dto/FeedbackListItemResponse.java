package com.yqz.openblog.feedback.dto;

import com.yqz.openblog.feedback.entity.FeedbackEntry;

import java.time.Instant;
import java.time.LocalDate;

public class FeedbackListItemResponse {
    private Long id;
    private String submitterName;
    private String content;
    private LocalDate submitDay;
    private FeedbackEntry.Status status;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public FeedbackEntry.Status getStatus() {
        return status;
    }

    public void setStatus(FeedbackEntry.Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

