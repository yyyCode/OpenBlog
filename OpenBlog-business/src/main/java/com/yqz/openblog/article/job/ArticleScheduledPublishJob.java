package com.yqz.openblog.article.job;

import com.yqz.openblog.article.service.ArticleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时发布：把到点的 SCHEDULED 文章自动发布为 PUBLISHED。
 *
 * 说明：多实例部署时通过“条件更新”保证只会发布一次，无需额外分布式锁。
 */
@Component
public class ArticleScheduledPublishJob {

    private final ArticleService articleService;

    public ArticleScheduledPublishJob(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Scheduled(fixedDelayString = "${openblog.article-schedule.scan-delay-ms:30000}")
    public void run() {
        // 每轮最多发布 N 篇，避免长事务/长循环；下一轮会继续扫。
        articleService.publishDueScheduled(200);
    }
}

