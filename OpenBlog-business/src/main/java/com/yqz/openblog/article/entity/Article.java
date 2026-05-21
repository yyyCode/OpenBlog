package com.yqz.openblog.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@TableName("articles")
@Table(name = "articles", indexes = {
        @Index(name = "idx_articles_author_id", columnList = "author_id"),
        @Index(name = "idx_articles_status_published_at", columnList = "status,published_at"),
        @Index(name = "idx_articles_title", columnList = "title")
})
public class Article {

    @TableId(value = "id", type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 255)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(length = 64)
    private String coverMediaKey;

    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ArticleStatus status;

    @Column
    private Instant publishedAt;

    /**
     * 定时发布的预约时间（UTC Instant）。当 status=SCHEDULED 时该字段必填。
     * 到点后由后台定时任务把文章置为 PUBLISHED，并把 publishedAt=scheduledAt。
     */
    @Column
    private Instant scheduledAt;

    @Column
    private Instant submittedAt;

    @Column
    private Instant reviewedAt;

    @Column(length = 512)
    private String rejectedReason;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private Long likeCount;

    @Column(nullable = false)
    @TableField("view_count")
    private Long viewCount;

    @Column(nullable = false)
    private Long favoriteCount;

    @Column(nullable = false)
    private Long commentCount;

    @Column(nullable = false)
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @Column(nullable = false)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (likeCount == null) likeCount = 0L;
        if (viewCount == null) viewCount = 0L;
        if (favoriteCount == null) favoriteCount = 0L;
        if (commentCount == null) commentCount = 0L;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Article() {
    }

    // getters/setters
    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public void setContentMarkdown(String contentMarkdown) {
        this.contentMarkdown = contentMarkdown;
    }

    public String getCoverMediaKey() {
        return coverMediaKey;
    }

    public void setCoverMediaKey(String coverMediaKey) {
        this.coverMediaKey = coverMediaKey;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public ArticleStatus getStatus() {
        return status;
    }

    public void setStatus(ArticleStatus status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getRejectedReason() {
        return rejectedReason;
    }

    public void setRejectedReason(String rejectedReason) {
        this.rejectedReason = rejectedReason;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public Long getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(Long favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public Long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(Long commentCount) {
        this.commentCount = commentCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

