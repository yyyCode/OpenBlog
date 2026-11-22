package com.yqz.openblog.search.service;

import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.search.core.SearchOps;
import com.yqz.openblog.search.dto.ArticleSearchReindexResult;
import com.yqz.openblog.search.index.SearchIndex;
import com.yqz.openblog.search.index.SearchIndexManager;
import com.yqz.openblog.search.index.SimpleSearchIndex;
import com.yqz.openblog.search.document.ArticleDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleSearchReindexServiceTest {

    private static final SearchIndex INDEX =
            new SimpleSearchIndex("test_articles", ArticleDocument.class);

    private ArticleMapper articleMapper;
    private ArticleIndexer articleIndexer;
    private SearchIndexManager indexManager;
    private SearchOps searchOps;
    private ObjectProvider<SearchIndexManager> indexManagerProvider;
    private ObjectProvider<SearchOps> searchOpsProvider;
    private ArticleSearchReindexService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        articleIndexer = mock(ArticleIndexer.class);
        indexManager = mock(SearchIndexManager.class);
        searchOps = mock(SearchOps.class);
        indexManagerProvider = mock(ObjectProvider.class);
        when(indexManagerProvider.getIfAvailable()).thenReturn(indexManager);
        searchOpsProvider = mock(ObjectProvider.class);
        service = new ArticleSearchReindexService(
                articleMapper, articleIndexer, indexManagerProvider, INDEX, searchOpsProvider);
    }

    @Test
    void reindexRejectsWhenEsIsDisabled() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.reindex())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("ES 搜索未启用");
        verify(indexManager, never()).recreateIndex(any());
    }

    @Test
    void reindexFailsWhenIndexCannotBeRecreated() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(searchOps);
        when(articleMapper.selectCount(any())).thenReturn(3L);
        when(indexManager.recreateIndex(INDEX)).thenReturn(false);

        assertThatThrownBy(() -> service.reindex())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("索引重建失败");
        verify(articleMapper, never()).selectList(any());
    }

    @Test
    void reindexRebuildsAllPublishedArticlesInBatches() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(searchOps);
        when(indexManager.recreateIndex(INDEX)).thenReturn(true);
        when(articleMapper.selectCount(any())).thenReturn(3L);
        when(articleMapper.selectList(any())).thenReturn(
                List.of(article(1L), article(2L)),
                List.of(article(3L)),
                List.of());
        when(articleIndexer.syncBatch(anyList())).thenReturn(2, 1);

        ArticleSearchReindexResult result = service.reindex();

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.indexed()).isEqualTo(3);
        assertThat(result.batches()).isEqualTo(2);
        assertThat(result.failedBatches()).isZero();
    }

    @Test
    void reindexCountsBatchAsFailedWhenNothingWasWritten() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(searchOps);
        when(indexManager.recreateIndex(INDEX)).thenReturn(true);
        when(articleMapper.selectCount(any())).thenReturn(2L);
        when(articleMapper.selectList(any())).thenReturn(
                List.of(article(1L), article(2L)),
                List.of());
        when(articleIndexer.syncBatch(anyList())).thenReturn(0);

        ArticleSearchReindexResult result = service.reindex();

        assertThat(result.indexed()).isZero();
        assertThat(result.failedBatches()).isEqualTo(1);
    }

    @Test
    void reindexOnlyRecreatesIndexWhenThereAreNoPublishedArticles() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(searchOps);
        when(indexManager.recreateIndex(INDEX)).thenReturn(true);
        when(articleMapper.selectCount(any())).thenReturn(0L);

        ArticleSearchReindexResult result = service.reindex();

        assertThat(result.total()).isZero();
        assertThat(result.indexed()).isZero();
        verify(articleMapper, never()).selectList(any());
    }

    private static Article article(long id) {
        Article article = new Article();
        ReflectionTestUtils.setField(article, "id", id);
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setAuthorId(1L);
        article.setPublishedAt(Instant.now());
        return article;
    }
}
