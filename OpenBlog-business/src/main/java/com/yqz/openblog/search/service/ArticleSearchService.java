package com.yqz.openblog.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.article.service.ArticleService;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.search.core.SearchHit;
import com.yqz.openblog.search.core.SearchOps;
import com.yqz.openblog.search.core.SearchRequest;
import com.yqz.openblog.search.core.SearchResult;
import com.yqz.openblog.search.core.SearchSort;
import com.yqz.openblog.search.document.ArticleDocument;
import com.yqz.openblog.search.index.SearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章全文搜索服务。
 *
 * <p>优先使用 ES（需 {@code openblog.search.enabled=true}）；ES 未启用、不可用或查询异常时
 * 自动降级为 MySQL LIKE + FULLTEXT。</p>
 */
@Service
public class ArticleSearchService {

    private static final Logger log = LoggerFactory.getLogger(ArticleSearchService.class);

    private final ArticleMapper articleMapper;
    private final ArticleService articleService;
    private final SearchIndex articleSearchIndex;
    private final ObjectProvider<SearchOps> searchOpsProvider;

    public ArticleSearchService(ArticleMapper articleMapper,
                                ArticleService articleService,
                                @Qualifier("articleSearchIndex") SearchIndex articleSearchIndex,
                                ObjectProvider<SearchOps> searchOpsProvider) {
        this.articleMapper = articleMapper;
        this.articleService = articleService;
        this.articleSearchIndex = articleSearchIndex;
        this.searchOpsProvider = searchOpsProvider;
    }

    /**
     * 全文搜索已发布文章。优先 ES，降级 MySQL。
     */
    public PageResult<ArticleListItemResponse> search(String keyword, int page, int size) {
        SearchOps searchOps = searchOpsProvider.getIfAvailable();
        if (searchOps == null) {
            return searchByMysql(keyword, page, size);
        }

        SearchRequest request = SearchRequest.builder()
                .keyword(keyword)
                .field("title", 3f)
                .field("summary", 2f)
                .field("contentMarkdown", 1f)
                .termFilter("status", ArticleStatus.PUBLISHED.name())
                .sort(SearchSort.desc("publishedAt"))
                .page(page)
                .size(size)
                .build();

        SearchResult<ArticleDocument> result =
                searchOps.search(articleSearchIndex, request, ArticleDocument.class);
        if (result.isDegraded()) {
            log.warn("ES 搜索不可用，降级到 MySQL LIKE，keyword={}", keyword);
            return searchByMysql(keyword, page, size);
        }
        return toPageResult(result, page, size);
    }

    /**
     * 按 ES 命中的顺序批量回查数据库（1 次查询），过滤掉已非发布状态的文章。
     */
    private PageResult<ArticleListItemResponse> toPageResult(SearchResult<ArticleDocument> result,
                                                             int page, int size) {
        List<Long> orderedIds = new ArrayList<>(result.getHits().size());
        for (SearchHit<ArticleDocument> hit : result.getHits()) {
            Long id = resolveArticleId(hit);
            if (id != null) {
                orderedIds.add(id);
            }
        }
        if (orderedIds.isEmpty()) {
            return new PageResult<>(List.of(), page, size, result.getTotalHits());
        }

        Map<Long, Article> publishedById = articleMapper.selectBatchIds(orderedIds).stream()
                .filter(a -> a.getStatus() == ArticleStatus.PUBLISHED)
                .collect(Collectors.toMap(Article::getId, Function.identity(), (a, b) -> a));

        List<Article> articles = orderedIds.stream()
                .map(publishedById::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageResult<>(articleService.mapListItems(articles), page, size, result.getTotalHits());
    }

    private static Long resolveArticleId(SearchHit<ArticleDocument> hit) {
        Long contentId = hit.getContent() == null ? null : hit.getContent().getId();
        if (contentId != null) {
            return contentId;
        }
        try {
            return Long.valueOf(hit.getId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * MySQL 降级搜索：标题和摘要 LIKE，正文 FULLTEXT MATCH。
     */
    private PageResult<ArticleListItemResponse> searchByMysql(String keyword, int page, int size) {
        // 搜索标题和摘要匹配的文章
        LambdaQueryWrapper<Article> wrapper = Wrappers.lambdaQuery(Article.class)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .and(w -> w
                        .like(Article::getTitle, keyword)
                        .or()
                        .like(Article::getSummary, keyword)
                )
                .orderByDesc(Article::getPublishedAt);

        IPage<Article> articlePage = articleMapper.selectPage(new Page<>(page + 1, size), wrapper);

        // 如果标题/摘要没有足够结果，尝试 FULLTEXT 搜索正文
        if (articlePage.getTotal() < size) {
            List<Long> fulltextIds = articleMapper.searchIdsByFulltext(keyword);
            if (!fulltextIds.isEmpty()) {
                // 合并结果：取已有结果 ID 集合，补充 FULLTEXT 结果
                Set<Long> existingIds = articlePage.getRecords().stream()
                        .map(Article::getId)
                        .collect(Collectors.toSet());
                for (Long id : fulltextIds) {
                    if (existingIds.size() >= size) break;
                    if (!existingIds.contains(id)) {
                        Article extra = articleMapper.selectById(id);
                        if (extra != null && extra.getStatus() == ArticleStatus.PUBLISHED) {
                            articlePage.getRecords().add(extra);
                            existingIds.add(id);
                        }
                    }
                }
                articlePage.setTotal(existingIds.size());
            }
        }

        List<ArticleListItemResponse> items = articleService.mapListItems(articlePage.getRecords());

        return new PageResult<>(items, page, size, articlePage.getTotal());
    }
}
