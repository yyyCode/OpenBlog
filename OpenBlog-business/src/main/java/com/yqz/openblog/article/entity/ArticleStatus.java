package com.yqz.openblog.article.entity;

public enum ArticleStatus {
    DRAFT,
    /**
     * 已预约（到达 scheduledAt 前不可见），由定时任务自动转为 PUBLISHED。
     */
    SCHEDULED,
    PUBLISHED,
    DELETED
}

