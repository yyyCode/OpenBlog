package com.yqz.openblog.article.service;

import com.yqz.openblog.audit.annotation.AuditLog;
import com.yqz.openblog.article.dto.ArticleCreateRequest;
import com.yqz.openblog.article.dto.ArticleDetailResponse;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.dto.ArticlePublishedContentCachePayload;
import com.yqz.openblog.article.dto.ArticleUpdateRequest;
import com.yqz.openblog.article.entity.*;
import com.yqz.openblog.article.repo.ArticleBodyMapper;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.category.service.CategoryService;
import com.yqz.openblog.search.core.SearchOps;
import com.yqz.openblog.search.model.ArticleDocument;
import com.yqz.openblog.seo.service.BaiduPushService;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.repo.UserMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleBodyMapper articleBodyMapper;
    private final UserMapper userMapper;
    private final ArticleViewService articleViewService;
    private final ArticlePublishedContentCacheService publishedContentCache;
    private final CategoryService categoryService;
    private final MarkdownRenderer markdownRenderer;
    private final BaiduPushService baiduPushService;

    @Autowired(required = false)
    private SearchOps searchOps;

    private static final String ES_INDEX_NAME = "openblog_articles";

    public ArticleService(
            ArticleMapper articleMapper,
            ArticleBodyMapper articleBodyMapper,
            UserMapper userMapper,
            ArticleViewService articleViewService,
            ArticlePublishedContentCacheService publishedContentCache,
            CategoryService categoryService,
            MarkdownRenderer markdownRenderer,
            BaiduPushService baiduPushService) {
        this.articleMapper = articleMapper;
        this.articleBodyMapper = articleBodyMapper;
        this.userMapper = userMapper;
        this.articleViewService = articleViewService;
        this.publishedContentCache = publishedContentCache;
        this.categoryService = categoryService;
        this.markdownRenderer = markdownRenderer;
        this.baiduPushService = baiduPushService;
    }

    public ArticleListItemResponse mapListItem(Article a) {
        ArticleListItemResponse resp = new ArticleListItemResponse();
        resp.setId(a.getId());
        resp.setTitle(a.getTitle());
        resp.setSummary(a.getSummary());
        resp.setCoverMediaKey(a.getCoverMediaKey());
        resp.setAuthorId(a.getAuthorId());
        User author = userMapper.selectById(a.getAuthorId());
        resp.setAuthorNickname(author == null ? null : author.getUsername());
        resp.setPublishedAt(a.getPublishedAt());
        resp.setStatus(a.getStatus());
        resp.setType(a.getType());
        resp.setLikeCount(a.getLikeCount());
        resp.setViewCount(a.getViewCount() == null ? 0L : a.getViewCount());
        resp.setFavoriteCount(a.getFavoriteCount());
        resp.setCommentCount(a.getCommentCount());
        applyCategory(resp, a.getCategoryId());
        return resp;
    }

    /**
     * 批量映射文章列表 — 预加载作者和分类信息，避免 N+1 查询。
     * <p>
     * 原 {@link #mapListItem(Article)} 每篇文章单独查作者和分类（N+1），
     * 此方法改为：1 次批量查所有作者 + 按需查分类（同一分类只查一次）。
     * 单篇文章场景仍可使用 mapListItem。
     */
    public List<ArticleListItemResponse> mapListItems(List<Article> articles) {
        if (articles.isEmpty()) return Collections.emptyList();

        // 批量加载所有作者：1 次 DB 查询
        Set<Long> authorIds = articles.stream()
                .map(Article::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        final Map<Long, User> userMap = authorIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        // 按 categoryId 缓存 resolveMeta 结果（resolveMeta 内部查全表，复用即可）
        Map<Long, CategoryService.CategoryMeta> catCache = new HashMap<>();

        return articles.stream().map(a -> {
            ArticleListItemResponse resp = new ArticleListItemResponse();
            resp.setId(a.getId());
            resp.setTitle(a.getTitle());
            resp.setSummary(a.getSummary());
            resp.setCoverMediaKey(a.getCoverMediaKey());
            resp.setAuthorId(a.getAuthorId());
            User author = userMap.get(a.getAuthorId());
            resp.setAuthorNickname(author != null ? author.getUsername() : null);
            resp.setPublishedAt(a.getPublishedAt());
            resp.setStatus(a.getStatus());
            resp.setLikeCount(a.getLikeCount());
            resp.setViewCount(a.getViewCount() == null ? 0L : a.getViewCount());
            resp.setFavoriteCount(a.getFavoriteCount());
            resp.setCommentCount(a.getCommentCount());

            CategoryService.CategoryMeta meta = catCache.computeIfAbsent(
                    a.getCategoryId(), categoryService::resolveMeta);
            resp.setCategoryId(meta.getCategoryId());
            resp.setCategoryName(meta.getCategoryName());
            resp.setCategoryPath(meta.getCategoryPath());

            return resp;
        }).collect(Collectors.toList());
    }

    public ArticleDetailResponse mapDetail(Article a) {
        ArticleDetailResponse resp = new ArticleDetailResponse();
        resp.setId(a.getId());
        resp.setTitle(a.getTitle());
        resp.setSummary(a.getSummary());
        resp.setCoverMediaKey(a.getCoverMediaKey());
        resp.setAuthorId(a.getAuthorId());
        User author = userMapper.selectById(a.getAuthorId());
        resp.setAuthorNickname(author == null ? null : author.getUsername());
        resp.setPublishedAt(a.getPublishedAt());
        resp.setStatus(a.getStatus());
        resp.setType(a.getType());
        resp.setLikeCount(a.getLikeCount());
        resp.setViewCount(a.getViewCount() == null ? 0L : a.getViewCount());
        resp.setFavoriteCount(a.getFavoriteCount());
        resp.setCommentCount(a.getCommentCount());
        resp.setCreatedAt(a.getCreatedAt());
        resp.setUpdatedAt(a.getUpdatedAt());
        applyCategory(resp, a.getCategoryId());
        attachBody(resp, a.getId());
        return resp;
    }

    private void applyCategory(ArticleListItemResponse resp, Long categoryId) {
        CategoryService.CategoryMeta meta = categoryService.resolveMeta(categoryId);
        resp.setCategoryId(meta.getCategoryId());
        resp.setCategoryName(meta.getCategoryName());
        resp.setCategoryPath(meta.getCategoryPath());
    }

    private void applyCategory(ArticleDetailResponse resp, Long categoryId) {
        CategoryService.CategoryMeta meta = categoryService.resolveMeta(categoryId);
        resp.setCategoryId(meta.getCategoryId());
        resp.setCategoryName(meta.getCategoryName());
        resp.setCategoryPath(meta.getCategoryPath());
    }

    /**
     * 从 article_bodies 加载正文，设置到响应中。
     * 对已迁移但未渲染 HTML 的旧文章，首次读取时惰性渲染并回填。
     */
    private void attachBody(ArticleDetailResponse resp, Long articleId) {
        ArticleBody body = articleBodyMapper.selectById(articleId);
        if (body == null) {
            return;
        }
        resp.setContentMarkdown(body.getContentMarkdown());
        if (body.getContentHtml() != null && !body.getContentHtml().isBlank()) {
            resp.setContentHtml(body.getContentHtml());
        } else {
            String html = markdownRenderer.render(body.getContentMarkdown());
            int wc = markdownRenderer.estimateWordCount(body.getContentMarkdown());
            resp.setContentHtml(html);
            articleBodyMapper.backfillHtmlIfNull(articleId, html, wc);
        }
    }

    /**
     * 缓存命中时：正文等用缓存，计数类字段始终用当前数据库行。
     */
    private ArticleDetailResponse mergePublishedDetailFromCache(ArticlePublishedContentCachePayload p, Article a) {
        ArticleDetailResponse r = new ArticleDetailResponse();
        r.setId(p.getId());
        r.setTitle(p.getTitle());
        r.setSummary(p.getSummary());
        r.setContentMarkdown(p.getContentMarkdown());
        r.setContentHtml(p.getContentHtml());
        r.setCoverMediaKey(p.getCoverMediaKey());
        r.setAuthorId(p.getAuthorId());
        r.setAuthorNickname(p.getAuthorNickname());
        r.setPublishedAt(p.getPublishedAt());
        r.setStatus(p.getStatus());
        r.setType(p.getType());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        r.setCategoryId(p.getCategoryId());
        r.setCategoryName(p.getCategoryName());
        r.setCategoryPath(p.getCategoryPath());
        r.setLikeCount(a.getLikeCount() == null ? 0L : a.getLikeCount());
        r.setViewCount(a.getViewCount() == null ? 0L : a.getViewCount());
        r.setFavoriteCount(a.getFavoriteCount() == null ? 0L : a.getFavoriteCount());
        r.setCommentCount(a.getCommentCount() == null ? 0L : a.getCommentCount());
        return r;
    }

    public PageResult<ArticleListItemResponse> listPublished(int page, int size, Long categoryId) {
        Optional<PageResult<ArticleListItemResponse>> cached = publishedContentCache.getList(categoryId, page, size);
        if (cached.isPresent()) {
            return cached.get();
        }

        Page<Article> mpPage = new Page<>(page + 1L, size);
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
        w.eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .eq(Article::getType, ArticleType.ARTICLE)
                .orderByDesc(Article::getPublishedAt);
        if (categoryId != null) {
            Set<Long> ids = categoryService.collectSelfAndDescendantIds(categoryId);
            if (ids.isEmpty()) {
                return new PageResult<>(List.of(), page, size, 0L);
            }
            w.in(Article::getCategoryId, ids);
        }
        IPage<Article> p = articleMapper.selectPage(mpPage, w);
        List<ArticleListItemResponse> items = mapListItems(p.getRecords());
        PageResult<ArticleListItemResponse> result = new PageResult<>(items, page, size, p.getTotal());
        publishedContentCache.putList(categoryId, page, size, result);
        return result;
    }

    public PageResult<ArticleListItemResponse> listByType(ArticleType type, int page, int size) {
        Page<Article> mpPage = new Page<>(page + 1L, size);
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
        w.eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .eq(Article::getType, type)
                .orderByDesc(Article::getPublishedAt);
        IPage<Article> p = articleMapper.selectPage(mpPage, w);
        List<ArticleListItemResponse> items = mapListItems(p.getRecords());
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
            applyCategory(resp, a.getCategoryId());
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
        boolean incremented = articleViewService.tryRecordView(a, clientIp);
        if (incremented) {
            long v = a.getViewCount() == null ? 0L : a.getViewCount();
            a.setViewCount(v + 1L);
        }
        return mapDetail(a);
    }

    @Transactional
    public ArticleListItemResponse createDraft(Long authorId, ArticleCreateRequest req) {
        Article a = new Article();
        a.setAuthorId(authorId);
        a.setTitle(req.getTitle());
        a.setSummary(req.getSummary());
        a.setCoverMediaKey(req.getCoverMediaKey());
        categoryService.validateCategoryId(req.getCategoryId());
        a.setCategoryId(req.getCategoryId());
        a.setStatus(ArticleStatus.DRAFT);
        a.setType(req.getType() != null ? ArticleType.valueOf(req.getType()) : ArticleType.ARTICLE);
        a.setLikeCount(0L);
        a.setViewCount(0L);
        a.setFavoriteCount(0L);
        a.setCommentCount(0L);
        articleMapper.insert(a);

        ArticleBody body = new ArticleBody();
        body.setArticleId(a.getId());
        body.setContentMarkdown(req.getContentMarkdown());
        body.setContentHtml(markdownRenderer.render(req.getContentMarkdown()));
        body.setWordCount(markdownRenderer.estimateWordCount(req.getContentMarkdown()));
        articleBodyMapper.insert(body);

        return mapListItem(a);
    }

    @Transactional
    public ArticleListItemResponse updateArticle(Long authorId, Long articleId, ArticleUpdateRequest req) {
        Article a = articleMapper.selectById(articleId);
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }
        if (!a.getAuthorId().equals(authorId)) {
            throw new BizException(4031, "无权限");
        }
        a.setTitle(req.getTitle());
        a.setSummary(req.getSummary());
        a.setCoverMediaKey(req.getCoverMediaKey());
        categoryService.validateCategoryId(req.getCategoryId());
        a.setCategoryId(req.getCategoryId());
        if (req.getType() != null) {
            a.setType(ArticleType.valueOf(req.getType()));
        }
        articleMapper.updateById(a);

        String renderedHtml = markdownRenderer.render(req.getContentMarkdown());
        int wc = markdownRenderer.estimateWordCount(req.getContentMarkdown());
        ArticleBody body = articleBodyMapper.selectById(articleId);
        if (body == null) {
            body = new ArticleBody();
            body.setArticleId(articleId);
            body.setContentMarkdown(req.getContentMarkdown());
            body.setContentHtml(renderedHtml);
            body.setWordCount(wc);
            articleBodyMapper.insert(body);
        } else {
            body.setContentMarkdown(req.getContentMarkdown());
            body.setContentHtml(renderedHtml);
            body.setWordCount(wc);
            articleBodyMapper.updateById(body);
        }

        if (a.getStatus() == ArticleStatus.PUBLISHED) {
            publishedContentCache.evict(articleId);
            publishedContentCache.evictPublishedList();
            syncToEs(a);
        }
        return mapListItem(a);
    }

    @Transactional
    public ArticleListItemResponse publish(Long authorId, Long articleId, Instant publishedAt) {
        Article a = articleMapper.selectById(articleId);
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }
        if (!a.getAuthorId().equals(authorId)) {
            throw new BizException(4031, "无权限");
        }
        ArticleBody body = articleBodyMapper.selectById(articleId);
        String md = body == null ? null : body.getContentMarkdown();
        if (a.getTitle() == null || a.getTitle().trim().isEmpty() || md == null || md.trim().isEmpty()) {
            throw new BizException(4002, "标题和正文不能为空");
        }

        Instant now = Instant.now();
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
        publishedContentCache.evictPublishedList();

        if (a.getStatus() == ArticleStatus.PUBLISHED) {
            baiduPushService.pushArticleUrl(articleId);
            syncToEs(a);
        }

        return mapListItem(a);
    }

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
        if (published > 0) {
            publishedContentCache.evictPublishedList();
        }
        return published;
    }

    @Transactional
    public void hide(Long authorId, Long articleId) {
        Article a = articleMapper.selectById(articleId);
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }
        if (!a.getAuthorId().equals(authorId)) {
            throw new BizException(4031, "无权限");
        }
        if (a.getStatus() == ArticleStatus.HIDDEN) {
            return;
        }
        a.setStatus(ArticleStatus.HIDDEN);
        a.setScheduledAt(null);
        articleMapper.updateById(a);
        publishedContentCache.evict(articleId);
        publishedContentCache.evictPublishedList();
        removeFromEs(articleId);
    }

    @AuditLog(action = "ARTICLE_DELETE", entityType = "Article", entityId = "#articleId")
    @Transactional
    public void deletePermanently(Long authorId, Long articleId) {
        Article a = articleMapper.selectById(articleId);
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }
        if (!a.getAuthorId().equals(authorId)) {
            throw new BizException(4031, "无权限");
        }
        // 先删正文，再删文章
        articleBodyMapper.deleteById(articleId);
        articleMapper.deleteById(articleId);
        publishedContentCache.evict(articleId);
        publishedContentCache.evictPublishedList();
        removeFromEs(articleId);
    }

    public PageResult<ArticleListItemResponse> listMine(Long authorId, int page, int size) {
        List<ArticleStatus> statuses = List.of(ArticleStatus.DRAFT, ArticleStatus.SCHEDULED, ArticleStatus.PUBLISHED, ArticleStatus.HIDDEN);
        Page<Article> mpPage = new Page<>(page + 1L, size);
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
        w.eq(Article::getAuthorId, authorId)
                .in(Article::getStatus, statuses)
                .orderByDesc(Article::getCreatedAt);
        IPage<Article> p = articleMapper.selectPage(mpPage, w);
        List<ArticleListItemResponse> items = mapListItems(p.getRecords());
        return new PageResult<>(items, page, size, p.getTotal());
    }

    // --- ES 索引同步 ---

    /**
     * 同步文章到 ES 索引（ES 未启用时静默跳过）。
     */
    private void syncToEs(Article article) {
        if (searchOps == null) return;
        try {
            ArticleBody body = articleBodyMapper.selectById(article.getId());
            ArticleDocument doc = new ArticleDocument();
            doc.setId(article.getId());
            doc.setTitle(article.getTitle());
            doc.setSummary(article.getSummary());
            doc.setContentMarkdown(body != null ? body.getContentMarkdown() : null);
            doc.setCategoryId(article.getCategoryId());
            doc.setCategoryName(categoryService.resolveMeta(article.getCategoryId()).getCategoryName());
            doc.setAuthorId(article.getAuthorId());
            User author = userMapper.selectById(article.getAuthorId());
            doc.setAuthorName(author != null ? author.getUsername() : null);
            doc.setPublishedAt(article.getPublishedAt() != null ? article.getPublishedAt().toString() : null);
            doc.setViewCount(article.getViewCount() != null ? article.getViewCount() : 0L);
            searchOps.index(ES_INDEX_NAME, String.valueOf(article.getId()), doc);
        } catch (Exception e) {
            // ES 同步失败不影响主流程
        }
    }

    /**
     * 从 ES 索引中移除文章（ES 未启用时静默跳过）。
     */
    private void removeFromEs(Long articleId) {
        if (searchOps == null) return;
        try {
            searchOps.delete(ES_INDEX_NAME, String.valueOf(articleId));
        } catch (Exception e) {
            // ES 同步失败不影响主流程
        }
    }
}
