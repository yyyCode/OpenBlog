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
import com.yqz.openblog.search.document.ArticleDocument;
import com.yqz.openblog.search.index.SearchIndex;
import com.yqz.openblog.search.index.SimpleSearchIndex;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleSearchServiceTest {

    private static final SearchIndex INDEX =
            new SimpleSearchIndex("test_articles", ArticleDocument.class);

    private ArticleMapper articleMapper;
    private ArticleService articleService;
    private SearchOps searchOps;
    private ObjectProvider<SearchOps> searchOpsProvider;
    private ArticleSearchService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        articleService = mock(ArticleService.class);
        searchOps = mock(SearchOps.class);
        searchOpsProvider = mock(ObjectProvider.class);
        service = new ArticleSearchService(articleMapper, articleService, INDEX, searchOpsProvider);
    }

    @Test
    void searchUsesEsWithRelevanceSortFiltersAndNormalizedPaging() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(searchOps);
        ArticleDocument doc = new ArticleDocument();
        doc.setId(10L);
        doReturnSearchResult(new SearchResult<>(
                List.of(new SearchHit<>("10", 1.2f, doc, Map.of())), 1, 0, 50, false));
        when(articleMapper.selectBatchIds(List.of(10L)))
                .thenReturn(List.of(article(10L, ArticleStatus.PUBLISHED)));
        when(articleService.mapListItems(anyList())).thenReturn(List.of());

        service.search("  spring  ", -3, 500, 7L, ArticleType.JOB_NAV);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(searchOps).search(eq(INDEX), captor.capture(), eq(ArticleDocument.class));
        SearchRequest request = captor.getValue();

        assertThat(request.getKeyword()).isEqualTo("spring");
        assertThat(request.getFields()).containsEntry("title", 3f)
                .containsEntry("summary", 2f)
                .containsEntry("contentMarkdown", 1f);
        assertThat(request.getTermFilters())
                .containsEntry("status", "PUBLISHED")
                .containsEntry("categoryId", 7L)
                .containsEntry("type", "JOB_NAV");
        assertThat(request.getPage()).isZero();
        assertThat(request.getSize()).isEqualTo(50);
        assertThat(request.getSorts()).hasSize(2);
        assertThat(request.getSorts().get(0).isScore()).isTrue();
        assertThat(request.getSorts().get(1).getField()).isEqualTo("publishedAt");
    }

    @Test
    void searchDegradesToMysqlWhenEsFails() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(searchOps);
        doReturnSearchResult(SearchResult.degraded(0, 20));
        when(articleMapper.countPublishedSearch(any(), any(), any(), any())).thenReturn(2L);
        when(articleMapper.searchPublishedIds(any(), any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(5L, 6L));
        when(articleMapper.selectBatchIds(List.of(5L, 6L))).thenReturn(List.of(
                article(5L, ArticleStatus.PUBLISHED), article(6L, ArticleStatus.PUBLISHED)));
        ArticleListItemResponse item = new ArticleListItemResponse();
        when(articleService.mapListItems(anyList())).thenReturn(List.of(item));

        PageResult<ArticleListItemResponse> result =
                service.search("50%_off", 0, 20, null, null);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getItems()).containsExactly(item);
        verify(searchOps).search(eq(INDEX), any(SearchRequest.class), eq(ArticleDocument.class));
        // LIKE 通配符必须转义，否则 % 与 _ 会退化成任意匹配
        verify(articleMapper).countPublishedSearch("50%_off", "%50\\%\\_off%", null, null);
        verify(articleMapper).searchPublishedIds(
                "50%_off", "%50\\%\\_off%", null, null, 0L, 20);
    }

    @Test
    void searchFallsBackToMysqlWhenEsIsDisabled() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(null);
        when(articleMapper.countPublishedSearch(any(), any(), any(), any())).thenReturn(0L);

        PageResult<ArticleListItemResponse> result = service.search("vue", 0, 20, null, null);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verifyNoInteractions(searchOps);
    }

    @Test
    void blankKeywordReturnsEmptyWithoutQueryingEsOrMysql() {
        PageResult<ArticleListItemResponse> result = service.search("   ", 2, 10, null, null);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        verifyNoInteractions(searchOps, articleMapper);
    }

    @Test
    void mysqlFallbackFiltersArticlesHiddenAfterQuery() {
        when(searchOpsProvider.getIfAvailable()).thenReturn(null);
        when(articleMapper.countPublishedSearch(any(), any(), any(), any())).thenReturn(2L);
        when(articleMapper.searchPublishedIds(any(), any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(5L, 6L));
        when(articleMapper.selectBatchIds(List.of(5L, 6L))).thenReturn(List.of(
                article(5L, ArticleStatus.PUBLISHED), article(6L, ArticleStatus.HIDDEN)));
        when(articleService.mapListItems(anyList())).thenReturn(List.of());

        service.search("vue", 0, 20, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Article>> captor = ArgumentCaptor.forClass(List.class);
        verify(articleService).mapListItems(captor.capture());
        assertThat(captor.getValue()).extracting(Article::getId).containsExactly(5L);
    }

    @SuppressWarnings("unchecked")
    private <T> void doReturnSearchResult(SearchResult<ArticleDocument> result) {
        when(searchOps.search(any(SearchIndex.class), any(SearchRequest.class), eq(ArticleDocument.class)))
                .thenReturn(result);
    }

    private static Article article(long id, ArticleStatus status) {
        Article article = new Article();
        ReflectionTestUtils.setField(article, "id", id);
        article.setStatus(status);
        article.setAuthorId(1L);
        article.setPublishedAt(Instant.now());
        return article;
    }
}
