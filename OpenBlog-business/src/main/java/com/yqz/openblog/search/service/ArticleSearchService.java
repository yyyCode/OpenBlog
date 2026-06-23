package com.yqz.openblog.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.repo.ArticleBodyMapper;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.article.service.ArticleService;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.search.core.SearchOps;
import com.yqz.openblog.search.core.SearchResult;
import com.yqz.openblog.user.repo.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文章全文搜索服务。
 * 优先使用 ES（需 openblog.search.enabled=true），ES 不可用时自动降级为 MySQL LIKE + FULLTEXT。
 */
@Service
public class ArticleSearchService {

    private static final Logger log = LoggerFactory.getLogger(ArticleSearchService.class);

    private static final String INDEX_NAME = "openblog_articles";
    private static final List<String> SEARCH_FIELDS = List.of("title", "summary", "contentMarkdown");

    @Autowired(required = false)
    private SearchOps searchOps;

    private final ArticleMapper articleMapper;
    private final ArticleBodyMapper articleBodyMapper;
    private final UserMapper userMapper;
    private final ArticleService articleService;

    public ArticleSearchService(ArticleMapper articleMapper,
                                ArticleBodyMapper articleBodyMapper,
                                UserMapper userMapper,
                                ArticleService articleService) {
        this.articleMapper = articleMapper;
        this.articleBodyMapper = articleBodyMapper;
        this.userMapper = userMapper;
        this.articleService = articleService;
    }

    /**
     * 全文搜索已发布文章。优先 ES，降级 MySQL。
     */
    public PageResult<ArticleListItemResponse> search(String keyword, int page, int size) {
        if (searchOps != null) {
            try {
                return searchByEs(keyword, page, size);
            } catch (Exception e) {
                log.warn("ES 搜索异常，降级到 MySQL LIKE，keyword={}", keyword, e);
            }
        }
        return searchByMysql(keyword, page, size);
    }

    /**
     * ES 搜索。
     */
    private PageResult<ArticleListItemResponse> searchByEs(String keyword, int page, int size) {
        SearchResult result = searchOps.search(INDEX_NAME, keyword, SEARCH_FIELDS, page, size);

        List<Article> articles = new ArrayList<>();
        for (Map<String, Object> hit : result.getHits()) {
            Object idObj = hit.get("id");
            if (idObj != null) {
                Long articleId = Long.valueOf(idObj.toString());
                Article article = articleMapper.selectById(articleId);
                if (article != null && article.getStatus() == ArticleStatus.PUBLISHED) {
                    articles.add(article);
                }
            }
        }

        List<ArticleListItemResponse> items = articleService.mapListItems(articles);
        return new PageResult<>(items, page, size, result.getTotalHits());
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
                java.util.Set<Long> existingIds = articlePage.getRecords().stream()
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
