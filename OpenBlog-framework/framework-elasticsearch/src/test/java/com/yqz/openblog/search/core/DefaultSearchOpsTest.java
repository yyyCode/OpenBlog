package com.yqz.openblog.search.core;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.yqz.openblog.search.index.SearchIndex;
import com.yqz.openblog.search.index.SimpleSearchIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSearchOpsTest {

    private static final SearchIndex INDEX = new SimpleSearchIndex("test_index", Doc.class);

    @Mock
    private ElasticsearchOperations esOps;

    private DefaultSearchOps ops;

    @BeforeEach
    void setUp() {
        ops = new DefaultSearchOps(esOps);
    }

    @Test
    void indexReturnsTrueOnSuccess() {
        assertThat(ops.index(INDEX, "1", new Doc("hello"))).isTrue();
    }

    @Test
    void indexReturnsFalseWhenEsFails() {
        when(esOps.index(any(IndexQuery.class), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("es down"));

        assertThat(ops.index(INDEX, "1", new Doc("hello"))).isFalse();
    }

    @Test
    void indexBatchSkipsEmptyInput() {
        assertThat(ops.indexBatch(INDEX, Map.of())).isTrue();

        verify(esOps, never()).bulkIndex(any(), any(IndexCoordinates.class));
    }

    @Test
    void indexBatchWritesAllDocuments() {
        Map<String, Doc> docs = new LinkedHashMap<>();
        docs.put("1", new Doc("a"));
        docs.put("2", new Doc("b"));

        assertThat(ops.indexBatch(INDEX, docs)).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IndexQuery>> captor = ArgumentCaptor.forClass(List.class);
        verify(esOps).bulkIndex(captor.capture(), any(IndexCoordinates.class));
        assertThat(captor.getValue()).extracting(IndexQuery::getId).containsExactly("1", "2");
    }

    @Test
    void indexBatchReturnsFalseWhenEsFails() {
        when(esOps.bulkIndex(any(), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("es down"));

        assertThat(ops.indexBatch(INDEX, Map.of("1", new Doc("a")))).isFalse();
    }

    @Test
    void deleteReturnsFalseWhenEsFails() {
        when(esOps.delete(eq("1"), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("es down"));

        assertThat(ops.delete(INDEX, "1")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchMapsHitsAndTotal() {
        org.springframework.data.elasticsearch.core.SearchHit<Doc> hit =
                mock(org.springframework.data.elasticsearch.core.SearchHit.class);
        when(hit.getId()).thenReturn("42");
        when(hit.getScore()).thenReturn(1.5f);
        when(hit.getContent()).thenReturn(new Doc("spring"));
        when(hit.getHighlightFields()).thenReturn(Map.of());

        SearchHits<Doc> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(hits.getTotalHits()).thenReturn(7L);
        when(esOps.search(any(Query.class), eq(Doc.class), any(IndexCoordinates.class))).thenReturn(hits);

        SearchResult<Doc> result = ops.search(INDEX, SearchRequest.builder()
                .keyword("spring")
                .field("title", 1f)
                .build(), Doc.class);

        assertThat(result.isDegraded()).isFalse();
        assertThat(result.getTotalHits()).isEqualTo(7L);
        assertThat(result.getHits()).hasSize(1);
        assertThat(result.getHits().get(0).getId()).isEqualTo("42");
        assertThat(result.getHits().get(0).getContent().title()).isEqualTo("spring");
    }

    @Test
    void searchReturnsDegradedWhenEsFails() {
        when(esOps.search(any(Query.class), eq(Doc.class), any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("es down"));

        SearchResult<Doc> result = ops.search(INDEX, SearchRequest.builder()
                .keyword("spring")
                .field("title", 1f)
                .page(2)
                .size(5)
                .build(), Doc.class);

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getHits()).isEmpty();
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
    }

    @Test
    void searchBuildsWeightedFieldsFiltersAndSort() {
        when(esOps.search(any(Query.class), eq(Doc.class), any(IndexCoordinates.class)))
                .thenReturn(mock(SearchHits.class));

        SearchRequest request = SearchRequest.builder()
                .keyword("spring")
                .field("title", 3f)
                .field("summary", null)
                .termFilter("status", "PUBLISHED")
                .sort(SearchSort.desc("publishedAt"))
                .page(0)
                .size(10)
                .build();

        ops.search(INDEX, request, Doc.class);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(esOps).search(captor.capture(), eq(Doc.class), any(IndexCoordinates.class));
        NativeQuery query = (NativeQuery) captor.getValue();

        assertThat(query.getQuery().multiMatch().fields()).containsExactly("title^3", "summary");
        assertThat(query.getQuery().multiMatch().query()).isEqualTo("spring");
        assertThat(query.getFilter().term().field()).isEqualTo("status");
        assertThat(query.getFilter().term().value().stringValue()).isEqualTo("PUBLISHED");
        assertThat(query.getSortOptions()).hasSize(1);
        assertThat(query.getSortOptions().get(0).field().field()).isEqualTo("publishedAt");
        assertThat(query.getSortOptions().get(0).field().order()).isEqualTo(SortOrder.Desc);
    }

    @Test
    void searchWithoutKeywordUsesMatchAll() {
        when(esOps.search(any(Query.class), eq(Doc.class), any(IndexCoordinates.class)))
                .thenReturn(mock(SearchHits.class));

        ops.search(INDEX, SearchRequest.builder().build(), Doc.class);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(esOps).search(captor.capture(), eq(Doc.class), any(IndexCoordinates.class));

        assertThat(((NativeQuery) captor.getValue()).getQuery().isMatchAll()).isTrue();
    }

    record Doc(String title) {
    }
}
