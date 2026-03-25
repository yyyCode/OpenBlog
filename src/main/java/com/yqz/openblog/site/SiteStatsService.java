package com.yqz.openblog.site;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.comment.entity.Comment;
import com.yqz.openblog.comment.entity.CommentStatus;
import com.yqz.openblog.comment.repo.CommentMapper;
import com.yqz.openblog.site.dto.SiteStatsResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SiteStatsService {

    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;

    public SiteStatsService(ArticleMapper articleMapper, CommentMapper commentMapper) {
        this.articleMapper = articleMapper;
        this.commentMapper = commentMapper;
    }

    public SiteStatsResponse getPublicStats() {
        long articleCount = articleMapper.selectCount(
                Wrappers.lambdaQuery(Article.class).eq(Article::getStatus, ArticleStatus.PUBLISHED));
        long commentCount = commentMapper.selectCount(
                Wrappers.lambdaQuery(Comment.class).eq(Comment::getStatus, CommentStatus.APPROVED));

        Article latestPublished = articleMapper.selectOne(
                Wrappers.lambdaQuery(Article.class)
                        .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                        .isNotNull(Article::getPublishedAt)
                        .orderByDesc(Article::getPublishedAt)
                        .last("LIMIT 1"));
        Instant lastActivity = latestPublished != null ? latestPublished.getPublishedAt() : null;

        return new SiteStatsResponse(articleCount, commentCount, lastActivity);
    }
}
