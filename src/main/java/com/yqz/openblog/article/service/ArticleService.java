package com.yqz.openblog.article.service;

import com.yqz.openblog.article.dto.ArticleCreateRequest;
import com.yqz.openblog.article.dto.ArticleDetailResponse;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final ArticleViewService articleViewService;

    public ArticleService(ArticleMapper articleMapper, UserMapper userMapper, ArticleViewService articleViewService) {
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.articleViewService = articleViewService;
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

    public PageResult<ArticleListItemResponse> listPublished(int page, int size) {
        Page<Article> mpPage = new Page<>(page + 1L, size);
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
        w.eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .orderByDesc(Article::getPublishedAt);
        IPage<Article> p = articleMapper.selectPage(mpPage, w);
        List<ArticleListItemResponse> items = p.getRecords().stream().map(this::mapListItem).toList();
        return new PageResult<>(items, page, size, p.getTotal());
    }

    public ArticleDetailResponse detailPublished(Long id, String viewerKey) {
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
        w.eq(Article::getId, id).eq(Article::getStatus, ArticleStatus.PUBLISHED);
        Article a = articleMapper.selectOne(w);
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }
        boolean incremented = articleViewService.tryRecordView(a, viewerKey);
        if (incremented) {
            a = articleMapper.selectOne(w);
            if (a == null) {
                throw new BizException(4041, "文章不存在");
            }
        }
        return mapDetail(a);
    }

    public ArticleDetailResponse detailMine(Long authorId, Long articleId, String viewerKey) {
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
        w.eq(Article::getId, articleId).eq(Article::getAuthorId, authorId);
        Article a = articleMapper.selectOne(w);
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }
        if (a.getStatus() == ArticleStatus.DELETED) {
            throw new BizException(4041, "文章不存在");
        }
        boolean incremented = articleViewService.tryRecordView(a, viewerKey);
        if (incremented) {
            a = articleMapper.selectOne(w);
            if (a == null) {
                throw new BizException(4041, "文章不存在");
            }
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
        return mapListItem(a);
    }

    public ArticleListItemResponse publish(Long authorId, Long articleId) {
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
        a.setStatus(ArticleStatus.PUBLISHED);
        a.setPublishedAt(Instant.now());
        a.setSubmittedAt(null);
        a.setReviewedAt(null);
        a.setRejectedReason(null);
        articleMapper.updateById(a);
        return mapListItem(a);
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
        articleMapper.updateById(a);
    }

    public PageResult<ArticleListItemResponse> listMine(Long authorId, int page, int size) {
        List<ArticleStatus> statuses = List.of(ArticleStatus.DRAFT, ArticleStatus.PUBLISHED);
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
