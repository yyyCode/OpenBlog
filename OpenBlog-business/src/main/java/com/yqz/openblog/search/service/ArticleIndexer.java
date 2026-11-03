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
            searchOps.index(articleSearchIndex, String.valueOf(article.getId()), toDocument(article));
        } catch (Exception e) {
            // 组装文档时的异常（如查正文/分类失败）同样不能影响主流程
            log.warn("组装文章索引文档失败（已忽略），articleId={}", article.getId(), e);
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

    private ArticleDocument toDocument(Article article) {
        ArticleBody body = articleBodyMapper.selectById(article.getId());
        User author = userMapper.selectById(article.getAuthorId());

        ArticleDocument doc = new ArticleDocument();
        doc.setId(article.getId());
        doc.setTitle(article.getTitle());
        doc.setSummary(article.getSummary());
        doc.setContentMarkdown(body == null ? null : body.getContentMarkdown());
        doc.setCategoryId(article.getCategoryId());
        doc.setCategoryName(categoryService.resolveMeta(article.getCategoryId()).getCategoryName());
        doc.setAuthorId(article.getAuthorId());
        doc.setAuthorName(author == null ? null : author.getUsername());
        doc.setStatus(article.getStatus().name());
        doc.setPublishedAt(article.getPublishedAt());
        doc.setViewCount(article.getViewCount() == null ? 0L : article.getViewCount());
        return doc;
    }
}
