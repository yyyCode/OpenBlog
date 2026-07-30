package com.yqz.openblog.article.entity;

public enum ArticleStatus {
    DRAFT,
    /**
     * 已预约（到达 scheduledAt 前不可见），由定时任务自动转为 PUBLISHED。
     */
    SCHEDULED,
    PUBLISHED,
    /**
     * 已隐藏（下架），不对公众展示，作者可在控制台重新发布。
     */
    HIDDEN
}

