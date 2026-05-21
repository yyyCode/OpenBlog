package com.yqz.openblog.comment.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CommentThreadResponse {
    private Long id;
    private Long articleId;
    private Long parentId;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    private CommentUserResponse user;
    private List<CommentThreadResponse> replies = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public CommentUserResponse getUser() {
        return user;
    }

    public void setUser(CommentUserResponse user) {
        this.user = user;
    }

    public List<CommentThreadResponse> getReplies() {
        return replies;
    }

    public void setReplies(List<CommentThreadResponse> replies) {
        this.replies = replies;
    }
}

