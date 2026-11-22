package com.yqz.openblog.search.service;

import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.entity.ArticleType;
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

    /** 关键词最大长度，过长的词会显著放大 ES 查询开销。 */
    private static final int MAX_KEYWORD_LENGTH = 100;
    /** 单页最大条数，防止调用方用超大 size 拖垮 ES/MySQL。 */
    private static final int MAX_PAGE_SIZE = 50;

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
     *
     * @param keyword    关键词（会 trim、截断到 100 字符）
     * @param page       页码（从 0 开始，负数按 0 处理）
     * @param size       每页条数（1..50）
     * @param categoryId 分类过滤，null 表示不过滤
     * @param type       文章类型过滤，null 表示不过滤
     */
    public PageResult<ArticleListItemResponse> search(String keyword, int page, int size,
                                                      Long categoryId, ArticleType type) {
        String kw = normalizeKeyword(keyword);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        if (kw.isEmpty()) {
            return new PageResult<>(List.of(), safePage, safeSize, 0);
        }

        SearchOps searchOps = searchOpsProvider.getIfAvailable();
        if (searchOps == null) {
            return searchByMysql(kw, safePage, safeSize, categoryId, type);
        }

        SearchRequest.Builder builder = SearchRequest.builder()
                .keyword(kw)
                .field("title", 3f)
                .field("summary", 2f)
                .field("contentMarkdown", 1f)
                .termFilter("status", ArticleStatus.PUBLISHED.name())
                // 先按相关性、同分再按发布时间；只按时间排序会丢掉 ES 的 _score
                .sort(SearchSort.relevance())
                .sort(SearchSort.desc("publishedAt"))
                .page(safePage)
                .size(safeSize);
        if (categoryId != null) {
            builder.termFilter("categoryId", categoryId);
        }
        if (type != null) {
            builder.termFilter("type", type.name());
        }

        SearchResult<ArticleDocument> result =
                searchOps.search(articleSearchIndex, builder.build(), ArticleDocument.class);
        if (result.isDegraded()) {
            log.warn("ES 搜索不可用，降级到 MySQL，keyword={}", kw);
            return searchByMysql(kw, safePage, safeSize, categoryId, type);
        }
        return toPageResult(result, safePage, safeSize);
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
     * MySQL 降级搜索：标题/摘要 LIKE + 正文 FULLTEXT，统一分页与总数。
     */
    private PageResult<ArticleListItemResponse> searchByMysql(String keyword, int page, int size,
                                                              Long categoryId, ArticleType type) {
        String typeName = type == null ? null : type.name();
        String likePattern = "%" + escapeLike(keyword) + "%";

        long total = articleMapper.countPublishedSearch(keyword, likePattern, categoryId, typeName);
        if (total == 0) {
            return new PageResult<>(List.of(), page, size, 0);
        }

        long offset = (long) page * size;
        List<Long> ids = articleMapper.searchPublishedIds(
                keyword, likePattern, categoryId, typeName, offset, size);
        if (ids.isEmpty()) {
            return new PageResult<>(List.of(), page, size, total);
        }

        // 保持命中顺序（MySQL 按发布时间倒序），并过滤查询期间状态变化的文章
        Map<Long, Article> byId = articleMapper.selectBatchIds(ids).stream()
                .filter(a -> a.getStatus() == ArticleStatus.PUBLISHED)
                .collect(Collectors.toMap(Article::getId, Function.identity(), (a, b) -> a));
        List<Article> articles = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return new PageResult<>(articleService.mapListItems(articles), page, size, total);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String kw = keyword.trim();
        return kw.length() <= MAX_KEYWORD_LENGTH ? kw : kw.substring(0, MAX_KEYWORD_LENGTH);
    }

    /** 转义 LIKE 通配符，避免用户输入 "%" / "_" 变成任意匹配。 */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
