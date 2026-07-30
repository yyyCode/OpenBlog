package com.yqz.openblog.article.dto;

import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.entity.ArticleType;

import java.time.Instant;

/**
 * 仅缓存「相对稳定」字段；阅读量、评论数等每次从数据库合并，避免长期不一致。
 */
public class ArticlePublishedContentCachePayload {

    private Long id;
    private String title;
    private String summary;
    private String contentMarkdown;
    private String contentHtml;
    private String coverMediaKey;
    private Long authorId;
    private String authorNickname;
    private Instant publishedAt;
    private ArticleStatus status;
    private ArticleType type;
    private Instant createdAt;
    private Instant updatedAt;
    private Long categoryId;
    private String categoryName;
    private java.util.List<String> categoryPath;

    public static ArticlePublishedContentCachePayload fromDetail(ArticleDetailResponse d) {
        ArticlePublishedContentCachePayload p = new ArticlePublishedContentCachePayload();
        p.setId(d.getId());
        p.setTitle(d.getTitle());
        p.setSummary(d.getSummary());
        p.setContentMarkdown(d.getContentMarkdown());
        p.setContentHtml(d.getContentHtml());
        p.setCoverMediaKey(d.getCoverMediaKey());
        p.setAuthorId(d.getAuthorId());
        p.setAuthorNickname(d.getAuthorNickname());
        p.setPublishedAt(d.getPublishedAt());
        p.setStatus(d.getStatus());
        p.setType(d.getType());
        p.setCreatedAt(d.getCreatedAt());
        p.setUpdatedAt(d.getUpdatedAt());
        p.setCategoryId(d.getCategoryId());
        p.setCategoryName(d.getCategoryName());
        p.setCategoryPath(d.getCategoryPath());
        return p;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getContentHtml() {
        return contentHtml;
    }

    public void setContentHtml(String contentHtml) {
        this.contentHtml = contentHtml;
    }

    public String getCoverMediaKey() {
        return coverMediaKey;
    }

    public void setCoverMediaKey(String coverMediaKey) {
        this.coverMediaKey = coverMediaKey;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getAuthorNickname() {
        return authorNickname;
    }

    public void setAuthorNickname(String authorNickname) {
        this.authorNickname = authorNickname;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public ArticleStatus getStatus() {
        return status;
    }

    public void setStatus(ArticleStatus status) {
        this.status = status;
    }

    public ArticleType getType() {
        return type;
    }

    public void setType(ArticleType type) {
        this.type = type;
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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public java.util.List<String> getCategoryPath() {
        return categoryPath;
    }

    public void setCategoryPath(java.util.List<String> categoryPath) {
        this.categoryPath = categoryPath;
    }
}
