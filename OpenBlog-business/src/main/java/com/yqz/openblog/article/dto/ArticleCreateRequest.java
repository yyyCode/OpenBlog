package com.yqz.openblog.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ArticleCreateRequest {
    @NotBlank
    @Size(min = 1, max = 120)
    private String title;

    @Size(max = 255)
    private String summary;

    @NotBlank
    private String contentMarkdown;

    @Size(max = 64)
    private String coverMediaKey;

    private Long categoryId;

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
}

