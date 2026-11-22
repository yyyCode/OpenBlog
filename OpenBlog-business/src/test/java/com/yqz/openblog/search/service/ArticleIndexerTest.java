package com.yqz.openblog.search.service;

import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleBody;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.entity.ArticleType;
import com.yqz.openblog.article.repo.ArticleBodyMapper;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.category.service.CategoryService;
import com.yqz.openblog.search.core.SearchOps;
import com.yqz.openblog.search.document.ArticleDocument;
import com.yqz.openblog.search.index.SearchIndex;
import com.yqz.openblog.search.index.SimpleSearchIndex;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.repo.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleIndexerTest {

    private static final SearchIndex INDEX =
            new SimpleSearchIndex("test_articles", ArticleDocument.class);

    private ArticleMapper articleMapper;
    private ArticleBodyMapper articleBodyMapper;
    private UserMapper userMapper;
    private CategoryService categoryService;
    private SearchOps searchOps;
    private ObjectProvider<SearchOps> searchOpsProvider;
    private ArticleIndexer indexer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        articleBodyMapper = mock(ArticleBodyMapper.class);
        userMapper = mock(UserMapper.class);
        categoryService = mock(CategoryService.class);
        searchOps = mock(SearchOps.class);
        searchOpsProvider = mock(ObjectProvider.class);
        indexer = new ArticleIndexer(articleMapper, articleBodyMapper, userMapper,
                categoryService, INDEX, searchOpsProvider);
    }

    @Test
    void syncIndexesPublishedArticleWithFullDocument() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(searchOps);
        Article article = article(10L, ArticleStatus.PUBLISHED);
        when(articleBodyMapper.selectBatchIds(anySet())).thenReturn(List.of(body(10L, "# hello")));
        when(userMapper.selectBatchIds(anySet())).thenReturn(List.of(user(1L, "yqz")));
        CategoryService.CategoryMeta meta = mock(CategoryService.CategoryMeta.class);
        when(meta.getCategoryName()).thenReturn("Java");
        when(categoryService.resolveMeta(3L)).thenReturn(meta);

        indexer.sync(article);

        ArgumentCaptor<ArticleDocument> captor = ArgumentCaptor.forClass(ArticleDocument.class);
        verify(searchOps).index(eq(INDEX), eq("10"), captor.capture());
        ArticleDocument doc = captor.getValue();
        assertThat(doc.getId()).isEqualTo(10L);
        assertThat(doc.getTitle()).isEqualTo("标题");
        assertThat(doc.getContentMarkdown()).isEqualTo("# hello");
        assertThat(doc.getAuthorName()).isEqualTo("yqz");
        assertThat(doc.getCategoryName()).isEqualTo("Java");
        assertThat(doc.getStatus()).isEqualTo("PUBLISHED");
        assertThat(doc.getType()).isEqualTo("ARTICLE");
        verify(articleBodyMapper).selectBatchIds(anySet());
        verify(userMapper).selectBatchIds(anySet());
    }

    @Test
    void syncDeletesDocumentWhenArticleIsNotPublished() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(searchOps);

        indexer.sync(article(10L, ArticleStatus.HIDDEN));

        verify(searchOps).delete(INDEX, "10");
        verify(searchOps, never()).index(any(), any(), any());
        verifyNoInteractions(articleBodyMapper, userMapper, categoryService);
    }

    @Test
    void syncDoesNothingWhenEsIsDisabled() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(null);

        indexer.sync(article(10L, ArticleStatus.PUBLISHED));

        verifyNoInteractions(searchOps, articleBodyMapper, userMapper, categoryService);
    }

    @Test
    void syncBatchBuildsDocumentsInOneBatchAndSkipsNonPublished() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(searchOps);
        when(articleBodyMapper.selectBatchIds(anySet())).thenReturn(List.of(
                body(10L, "a"), body(11L, "b")));
        when(userMapper.selectBatchIds(anySet())).thenReturn(List.of(user(1L, "yqz")));
        CategoryService.CategoryMeta meta = mock(CategoryService.CategoryMeta.class);
        when(meta.getCategoryName()).thenReturn("Java");
        when(categoryService.resolveMeta(3L)).thenReturn(meta);
        when(searchOps.indexBatch(eq(INDEX), any())).thenReturn(true);

        int written = indexer.syncBatch(List.of(
                article(10L, ArticleStatus.PUBLISHED),
                article(11L, ArticleStatus.PUBLISHED),
                article(12L, ArticleStatus.DRAFT)));

        assertThat(written).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ArticleDocument>> captor = ArgumentCaptor.forClass(Map.class);
        verify(searchOps).indexBatch(eq(INDEX), captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys("10", "11");
        // 批量路径只查一次正文、一次作者
        verify(articleBodyMapper).selectBatchIds(anySet());
        verify(userMapper).selectBatchIds(anySet());
    }

    @Test
    void syncBatchReturnsZeroWhenBulkWriteFails() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(searchOps);
        when(articleBodyMapper.selectBatchIds(anySet())).thenReturn(List.of(body(10L, "a")));
        when(userMapper.selectBatchIds(anySet())).thenReturn(List.of());
        when(categoryService.resolveMeta(3L)).thenReturn(CategoryService.CategoryMeta.empty());
        when(searchOps.indexBatch(eq(INDEX), any())).thenReturn(false);

        assertThat(indexer.syncBatch(List.of(article(10L, ArticleStatus.PUBLISHED)))).isZero();
    }

    private static Article article(long id, ArticleStatus status) {
        Article article = new Article();
        ReflectionTestUtils.setField(article, "id", id);
        article.setTitle("标题");
        article.setSummary("摘要");
        article.setCategoryId(3L);
        article.setAuthorId(1L);
        article.setStatus(status);
        article.setType(ArticleType.ARTICLE);
        article.setPublishedAt(Instant.parse("2026-01-01T00:00:00Z"));
        article.setViewCount(9L);
        return article;
    }

    private static ArticleBody body(long articleId, String markdown) {
        ArticleBody body = new ArticleBody();
        body.setArticleId(articleId);
        body.setContentMarkdown(markdown);
        return body;
    }

    private static User user(long id, String username) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        return user;
    }
}
