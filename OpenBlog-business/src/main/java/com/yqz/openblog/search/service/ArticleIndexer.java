package com.yqz.openblog.search.service;

import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleBody;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.repo.ArticleBodyMapper;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.category.service.CategoryService;
import com.yqz.openblog.search.core.SearchOps;
import com.yqz.openblog.search.document.ArticleDocument;
import com.yqz.openblog.search.index.SearchIndex;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.repo.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章索引同步：负责把文章组装成 {@link ArticleDocument} 并写入/移出 ES。
 *
 * <p>ES 未启用时所有方法静默跳过；任何异常都只打 WARN，不影响文章主流程。</p>
 */
@Service
public class ArticleIndexer {

    private static final Logger log = LoggerFactory.getLogger(ArticleIndexer.class);

    private final ArticleMapper articleMapper;
    private final ArticleBodyMapper articleBodyMapper;
    private final UserMapper userMapper;
    private final CategoryService categoryService;
    private final SearchIndex articleSearchIndex;
    private final ObjectProvider<SearchOps> searchOpsProvider;

    public ArticleIndexer(ArticleMapper articleMapper,
                          ArticleBodyMapper articleBodyMapper,
                          UserMapper userMapper,
                          CategoryService categoryService,
                          @Qualifier("articleSearchIndex") SearchIndex articleSearchIndex,
                          ObjectProvider<SearchOps> searchOpsProvider) {
        this.articleMapper = articleMapper;
        this.articleBodyMapper = articleBodyMapper;
        this.userMapper = userMapper;
        this.categoryService = categoryService;
        this.articleSearchIndex = articleSearchIndex;
        this.searchOpsProvider = searchOpsProvider;
    }

    /**
     * 同步一篇文章：已发布则写入索引，其它状态则从索引移除。
     */
    public void sync(Article article) {
        if (article == null || article.getId() == null) {
            return;
        }
        SearchOps searchOps = searchOpsProvider.getIfAvailable();
        if (searchOps == null) {
            return;
        }
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            searchOps.delete(articleSearchIndex, String.valueOf(article.getId()));
            return;
        }
        try {
            ArticleDocument doc = buildDocuments(List.of(article))
                    .get(String.valueOf(article.getId()));
            searchOps.index(articleSearchIndex, String.valueOf(article.getId()), doc);
        } catch (Exception e) {
            // 组装文档时的异常（如查正文/分类失败）同样不能影响主流程
            log.warn("组装文章索引文档失败（已忽略），articleId={}", article.getId(), e);
        }
    }

    /**
     * 批量同步（全量重建索引用）：批量加载正文与作者，避免逐篇 N+1。
     *
     * <p>只写入 PUBLISHED 文章；调用方需自行确保索引是干净的（见重建流程先删索引）。</p>
     *
     * @param articles 待同步文章
     * @return 实际写入的文档数；ES 未启用或写入失败返回 0
     */
    public int syncBatch(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return 0;
        }
        List<Article> published = articles.stream()
                .filter(a -> a != null && a.getId() != null && a.getStatus() == ArticleStatus.PUBLISHED)
                .toList();
        if (published.isEmpty()) {
            return 0;
        }
        SearchOps searchOps = searchOpsProvider.getIfAvailable();
        if (searchOps == null) {
            return 0;
        }
        try {
            Map<String, ArticleDocument> docs = buildDocuments(published);
            return searchOps.indexBatch(articleSearchIndex, docs) ? docs.size() : 0;
        } catch (Exception e) {
            log.warn("批量组装文章索引文档失败（已忽略），size={}", published.size(), e);
            return 0;
        }
    }

    /**
     * 按 ID 重新加载并同步，供定时发布等只有 ID 的场景使用。
     */
    public void syncById(Long articleId) {
        if (articleId == null) {
            return;
        }
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            remove(articleId);
        } else {
            sync(article);
        }
    }

    /**
     * 从索引移除一篇文章。
     */
    public void remove(Long articleId) {
        if (articleId == null) {
            return;
        }
        SearchOps searchOps = searchOpsProvider.getIfAvailable();
        if (searchOps == null) {
            return;
        }
        searchOps.delete(articleSearchIndex, String.valueOf(articleId));
    }

    /**
     * 按文章 ID 批量组装索引文档：正文、作者各一次查询，分类名按分类维度去重查询。
     */
    private Map<String, ArticleDocument> buildDocuments(List<Article> articles) {
        Set<Long> articleIds = articles.stream()
                .map(Article::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ArticleBody> bodyById = articleIds.isEmpty()
                ? Map.of()
                : articleBodyMapper.selectBatchIds(articleIds).stream()
                        .collect(Collectors.toMap(ArticleBody::getArticleId, Function.identity(),
                                (a, b) -> a));

        Set<Long> authorIds = articles.stream()
                .map(Article::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> authorById = authorIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        Map<Long, String> categoryNameById = new HashMap<>();
        Map<String, ArticleDocument> result = new LinkedHashMap<>();
        for (Article article : articles) {
            ArticleBody body = bodyById.get(article.getId());
            User author = authorById.get(article.getAuthorId());

            ArticleDocument doc = new ArticleDocument();
            doc.setId(article.getId());
            doc.setTitle(article.getTitle());
            doc.setSummary(article.getSummary());
            doc.setContentMarkdown(body == null ? null : body.getContentMarkdown());
            doc.setCategoryId(article.getCategoryId());
            doc.setCategoryName(article.getCategoryId() == null ? null
                    : categoryNameById.computeIfAbsent(article.getCategoryId(),
                            id -> categoryService.resolveMeta(id).getCategoryName()));
            doc.setAuthorId(article.getAuthorId());
            doc.setAuthorName(author == null ? null : author.getUsername());
            doc.setStatus(article.getStatus().name());
            doc.setType(article.getType() == null ? null : article.getType().name());
            doc.setPublishedAt(article.getPublishedAt());
            doc.setViewCount(article.getViewCount() == null ? 0L : article.getViewCount());
            result.put(String.valueOf(article.getId()), doc);
        }
        return result;
    }
}
