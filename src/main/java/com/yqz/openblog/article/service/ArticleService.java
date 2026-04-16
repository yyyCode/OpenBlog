package com.yqz.openblog.article.service;

import com.yqz.openblog.article.dto.ArticleCreateRequest;
import com.yqz.openblog.article.dto.ArticleDetailResponse;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.dto.ArticlePublishedContentCachePayload;
import com.yqz.openblog.article.dto.ArticleUpdateRequest;
import com.yqz.openblog.article.entity.*;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.repo.UserMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final ArticleViewService articleViewService;
    private final ArticlePublishedContentCacheService publishedContentCache;

    public ArticleService(
            ArticleMapper articleMapper,
            UserMapper userMapper,
            ArticleViewService articleViewService,
            ArticlePublishedContentCacheService publishedContentCache) {
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.articleViewService = articleViewService;
        this.publishedContentCache = publishedContentCache;
    }

    public ArticleListItemResponse mapListItem(Article a) {
        ArticleListItemResponse resp = new ArticleListItemResponse();
        resp.setId(a.getId());
        resp.setTitle(a.getTitle());
        resp.setSummary(a.getSummary());
        resp.setCoverMediaKey(a.getCoverMediaKey());
        resp.setAuthorId(a.getAuthorId());
        User author = userMapper.selectById(a.getAuthorId());
        resp.setAuthorNickname(author == null ? null : author.getNickname());
        resp.setPublishedAt(a.getPublishedAt());
        resp.setStatus(a.getStatus());
        resp.setLikeCount(a.getLikeCount());
        resp.setViewCount(a.getViewCount() == null ? 0L : a.getViewCount());
        resp.setFavoriteCount(a.getFavoriteCount());
        resp.setCommentCount(a.getCommentCount());
        return resp;
    }

    public ArticleDetailResponse mapDetail(Article a) {
        ArticleDetailResponse resp = new ArticleDetailResponse();
        resp.setId(a.getId());
        resp.setTitle(a.getTitle());
        resp.setSummary(a.getSummary());
        resp.setContentMarkdown(a.getContentMarkdown());
        resp.setCoverMediaKey(a.getCoverMediaKey());
        resp.setAuthorId(a.getAuthorId());
        User author = userMapper.selectById(a.getAuthorId());
        resp.setAuthorNickname(author == null ? null : author.getNickname());
        resp.setPublishedAt(a.getPublishedAt());
        resp.setStatus(a.getStatus());
        resp.setLikeCount(a.getLikeCount());
        resp.setViewCount(a.getViewCount() == null ? 0L : a.getViewCount());
        resp.setFavoriteCount(a.getFavoriteCount());
        resp.setCommentCount(a.getCommentCount());
        resp.setCreatedAt(a.getCreatedAt());
        resp.setUpdatedAt(a.getUpdatedAt());
        return resp;
    }

    /**
     * 缓存命中时：正文等用缓存，计数类字段始终用当前数据库行（避免阅读量/评论数长期失真）。
     */
    private ArticleDetailResponse mergePublishedDetailFromCache(ArticlePublishedContentCachePayload p, Article a) {
        ArticleDetailResponse r = new ArticleDetailResponse();
        r.setId(p.getId());
        r.setTitle(p.getTitle());
        r.setSummary(p.getSummary());
        r.setContentMarkdown(p.getContentMarkdown());
        r.setCoverMediaKey(p.getCoverMediaKey());
        r.setAuthorId(p.getAuthorId());
        r.setAuthorNickname(p.getAuthorNickname());
        r.setPublishedAt(p.getPublishedAt());
        r.setStatus(p.getStatus());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        r.setLikeCount(a.getLikeCount() == null ? 0L : a.getLikeCount());
        r.setViewCount(a.getViewCount() == null ? 0L : a.getViewCount());
        r.setFavoriteCount(a.getFavoriteCount() == null ? 0L : a.getFavoriteCount());
        r.setCommentCount(a.getCommentCount() == null ? 0L : a.getCommentCount());
        return r;
    }

    public PageResult<ArticleListItemResponse> listPublished(int page, int size) {
        Page<Article> mpPage = new Page<>(page + 1L, size);
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
        w.eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .orderByDesc(Article::getPublishedAt);
        IPage<Article> p = articleMapper.selectPage(mpPage, w);
        List<ArticleListItemResponse> items = p.getRecords().stream().map(this::mapListItem).toList();
        return new PageResult<>(items, page, size, p.getTotal());
    }

    public ArticleDetailResponse detailPublished(Long id, String clientIp) {
        Article a = articleMapper.selectById(id);
        if (a == null || a.getStatus() != ArticleStatus.PUBLISHED) {
            publishedContentCache.evict(id);
            throw new BizException(4041, "文章不存在");
        }

        ArticleDetailResponse resp;
        Optional<ArticlePublishedContentCachePayload> cached = publishedContentCache.get(id);
        if (cached.isPresent()) {
            resp = mergePublishedDetailFromCache(cached.get(), a);
        } else {
            resp = mapDetail(a);
            publishedContentCache.put(id, ArticlePublishedContentCachePayload.fromDetail(resp));
        }

        boolean incremented = articleViewService.tryRecordView(a, clientIp);
        if (incremented) {
            long v = resp.getViewCount() == null ? 0L : resp.getViewCount();
            resp.setViewCount(v + 1L);
        }
        return resp;
    }

    public ArticleDetailResponse detailMine(Long authorId, Long articleId, String clientIp) {
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
        w.eq(Article::getId, articleId).eq(Article::getAuthorId, authorId);
        Article a = articleMapper.selectOne(w);
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }
        if (a.getStatus() == ArticleStatus.DELETED) {
            throw new BizException(4041, "文章不存在");
        }
        boolean incremented = articleViewService.tryRecordView(a, clientIp);
        if (incremented) {
            long v = a.getViewCount() == null ? 0L : a.getViewCount();
            a.setViewCount(v + 1L);
        }
        return mapDetail(a);
    }

    public ArticleListItemResponse createDraft(Long authorId, ArticleCreateRequest req) {
        Article a = new Article();
        a.setAuthorId(authorId);
        a.setTitle(req.getTitle());
        a.setSummary(req.getSummary());
        a.setContentMarkdown(req.getContentMarkdown());
        a.setCoverMediaKey(req.getCoverMediaKey());
        a.setCategoryId(req.getCategoryId());
        a.setStatus(ArticleStatus.DRAFT);
        a.setLikeCount(0L);
        a.setViewCount(0L);
        a.setFavoriteCount(0L);
        a.setCommentCount(0L);
        articleMapper.insert(a);
        return mapListItem(a);
    }

    public ArticleListItemResponse updateArticle(Long authorId, Long articleId, ArticleUpdateRequest req) {
        Article a = articleMapper.selectById(articleId);
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }
        if (!a.getAuthorId().equals(authorId)) {
            throw new BizException(4031, "无权限");
        }
        if (a.getStatus() == ArticleStatus.DELETED) {
            throw new BizException(4091, "当前文章不可编辑");
        }
        a.setTitle(req.getTitle());
        a.setSummary(req.getSummary());
        a.setContentMarkdown(req.getContentMarkdown());
        a.setCoverMediaKey(req.getCoverMediaKey());
        a.setCategoryId(req.getCategoryId());
        articleMapper.updateById(a);
        if (a.getStatus() == ArticleStatus.PUBLISHED) {
            publishedContentCache.evict(articleId);
        }
        return mapListItem(a);
    }

    public ArticleListItemResponse publish(Long authorId, Long articleId, Instant publishedAt) {
        Article a = articleMapper.selectById(articleId);
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }
        if (!a.getAuthorId().equals(authorId)) {
            throw new BizException(4031, "无权限");
        }
        if (a.getStatus() == ArticleStatus.DELETED) throw new BizException(4091, "当前文章不可发布");
        if (a.getTitle() == null || a.getTitle().trim().isEmpty() || a.getContentMarkdown() == null || a.getContentMarkdown().trim().isEmpty()) {
            throw new BizException(4002, "标题和正文不能为空");
        }
        Instant now = Instant.now();
        // 未来时间：按“定时发布”处理（文章在到点前不可见）
        if (publishedAt != null && publishedAt.isAfter(now)) {
            a.setStatus(ArticleStatus.SCHEDULED);
            a.setScheduledAt(publishedAt);
            a.setPublishedAt(null);
        } else {
            a.setStatus(ArticleStatus.PUBLISHED);
            a.setPublishedAt(publishedAt == null ? now : publishedAt);
            a.setScheduledAt(null);
        }
        a.setSubmittedAt(null);
        a.setReviewedAt(null);
        a.setRejectedReason(null);
        articleMapper.updateById(a);
        publishedContentCache.evict(articleId);
        return mapListItem(a);
    }

    /**
     * 定时任务扫描到点文章并发布。
     *
     * @return 本轮成功发布数量（可能为 0）
     */
    public int publishDueScheduled(int batchSize) {
        int limit = Math.max(1, Math.min(batchSize, 500));
        Instant now = Instant.now();
        List<Long> ids = articleMapper.listDueScheduledIds(now, limit);
        if (ids.isEmpty()) return 0;

        int published = 0;
        for (Long id : ids) {
            int updated = articleMapper.publishScheduledIfDue(id, now);
            if (updated > 0) {
                published++;
                publishedContentCache.evict(id);
            }
        }
        return published;
    }

    public void unpublishOrDelete(Long authorId, Long articleId) {
        Article a = articleMapper.selectById(articleId);
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }
        if (!a.getAuthorId().equals(authorId)) {
            throw new BizException(4031, "无权限");
        }
        if (a.getStatus() == ArticleStatus.DELETED) {
            return;
        }
        a.setStatus(ArticleStatus.DELETED);
        a.setScheduledAt(null);
        articleMapper.updateById(a);
        publishedContentCache.evict(articleId);
    }

    public PageResult<ArticleListItemResponse> listMine(Long authorId, int page, int size) {
        List<ArticleStatus> statuses = List.of(ArticleStatus.DRAFT, ArticleStatus.SCHEDULED, ArticleStatus.PUBLISHED);
        Page<Article> mpPage = new Page<>(page + 1L, size);
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
        w.eq(Article::getAuthorId, authorId)
                .in(Article::getStatus, statuses)
                .orderByDesc(Article::getCreatedAt);
        IPage<Article> p = articleMapper.selectPage(mpPage, w);
        List<ArticleListItemResponse> items = p.getRecords().stream().map(this::mapListItem).toList();
        return new PageResult<>(items, page, size, p.getTotal());
    }
}
