package com.yqz.openblog.site.dto;

import java.time.Instant;

public class SiteStatsResponse {

    private long articleCount;
    private long commentCount;
    /** 全站访问累计（按 IP 去重后累加） */
    private long siteVisitCount;
    /** 最近一篇已发布文章的发布时间（仅统计发文）；可能为 null */
    private Instant lastActivityAt;

    public SiteStatsResponse() {
    }

    public SiteStatsResponse(long articleCount, long commentCount, long siteVisitCount, Instant lastActivityAt) {
        this.articleCount = articleCount;
        this.commentCount = commentCount;
        this.siteVisitCount = siteVisitCount;
        this.lastActivityAt = lastActivityAt;
    }

    public long getArticleCount() {
        return articleCount;
    }

    public void setArticleCount(long articleCount) {
        this.articleCount = articleCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public long getSiteVisitCount() {
        return siteVisitCount;
    }

    public void setSiteVisitCount(long siteVisitCount) {
        this.siteVisitCount = siteVisitCount;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
}
