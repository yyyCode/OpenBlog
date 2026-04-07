package com.yqz.openblog.article.dto;

import java.time.Instant;

public class ArticlePublishRequest {
    /**
     * 可选发布时间；为空则使用当前时间。
     * 允许早于当前时间（用于回填历史文章发布时间）。
     */
    private Instant publishedAt;

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}

