package com.yqz.openblog.search.core;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.yqz.openblog.search.index.SearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link SearchOps} 的默认实现，基于 {@link ElasticsearchOperations}。
 * 所有方法内置 try-catch，ES 不可用时降级返回安全默认值。
 */
public class DefaultSearchOps implements SearchOps {

    private static final Logger log = LoggerFactory.getLogger(DefaultSearchOps.class);

    private final ElasticsearchOperations esOps;

    public DefaultSearchOps(ElasticsearchOperations esOps) {
        this.esOps = esOps;
    }

    @Override
    public <T> boolean index(SearchIndex index, String id, T document) {
        try {
            IndexQuery query = new IndexQueryBuilder()
                    .withId(id)
                    .withObject(document)
                    .build();
            esOps.index(query, IndexCoordinates.of(index.indexName()));
            return true;
        } catch (Exception e) {
            log.warn("ES index 失败（已忽略），index={}, id={}", index.indexName(), id, e);
            return false;
        }
    }

    @Override
    public <T> boolean indexBatch(SearchIndex index, Map<String, T> documentsById) {
        if (documentsById == null || documentsById.isEmpty()) {
            return true;
        }
        try {
            List<IndexQuery> queries = new ArrayList<>(documentsById.size());
            documentsById.forEach((id, document) -> {
                if (document != null) {
                    queries.add(new IndexQueryBuilder().withId(id).withObject(document).build());
                }
            });
            if (!queries.isEmpty()) {
                esOps.bulkIndex(queries, IndexCoordinates.of(index.indexName()));
            }
            return true;
        } catch (Exception e) {
            log.warn("ES bulkIndex 失败（已忽略），index={}, size={}", index.indexName(),
                    documentsById.size(), e);
            return false;
        }
    }

    @Override
    public boolean delete(SearchIndex index, String id) {
        try {
            esOps.delete(id, IndexCoordinates.of(index.indexName()));
            return true;
        } catch (Exception e) {
            log.warn("ES delete 失败（已忽略），index={}, id={}", index.indexName(), id, e);
            return false;
        }
    }

    @Override
    public <T> SearchResult<T> search(SearchIndex index, SearchRequest request, Class<T> type) {
        NativeQueryBuilder builder = NativeQuery.builder();

        if (request.hasKeyword()) {
            List<String> fields = toWeightedFields(request.getFields());
            builder.withQuery(q -> q.multiMatch(mm -> mm
                    .query(request.getKeyword())
                    .fields(fields)
                    .type(TextQueryType.BestFields)));
        } else {
            builder.withQuery(q -> q.matchAll(m -> m));
        }

        request.getTermFilters().forEach((field, value) ->
                builder.withFilter(f -> f.term(t -> t.field(field).value(toFieldValue(value)))));

        for (SearchSort sort : request.getSorts()) {
            SortOrder order = sort.getDirection() == SearchSort.Direction.ASC
                    ? SortOrder.Asc : SortOrder.Desc;
            builder.withSort(s -> s.field(f -> f.field(sort.getField()).order(order)));
        }

        builder.withPageable(PageRequest.of(request.getPage(), request.getSize()));
        return execute(index, builder.build(), type, request.getPage(), request.getSize());
    }

    @Override
    public <T> SearchResult<T> search(SearchIndex index, Query query, Class<T> type) {
        Pageable pageable = query.getPageable();
        int page = pageable == null || pageable.isUnpaged() ? 0 : pageable.getPageNumber();
        int size = pageable == null || pageable.isUnpaged() ? 0 : pageable.getPageSize();
        return execute(index, query, type, page, size);
    }

    private <T> SearchResult<T> execute(SearchIndex index, Query query, Class<T> type, int page, int size) {
        try {
            SearchHits<T> hits = esOps.search(query, type, IndexCoordinates.of(index.indexName()));
            List<SearchHit<T>> result = new ArrayList<>(hits.getSearchHits().size());
            for (org.springframework.data.elasticsearch.core.SearchHit<T> hit : hits.getSearchHits()) {
                result.add(new SearchHit<>(hit.getId(), hit.getScore(), hit.getContent(),
                        hit.getHighlightFields()));
            }
            return new SearchResult<>(result, hits.getTotalHits(), page, size, false);
        } catch (Exception e) {
            log.warn("ES search 失败（已降级），index={}", index.indexName(), e);
            return SearchResult.degraded(page, size);
        }
    }

    /** ES multi_match 的 fields 支持 {@code field^boost} 内联权重。 */
    private static List<String> toWeightedFields(Map<String, Float> fields) {
        List<String> result = new ArrayList<>(fields.size());
        fields.forEach((field, boost) -> {
            if (boost == null || boost <= 0f || boost == 1f) {
                result.add(field);
            } else {
                // 整数权重输出 "title^3" 而不是 "title^3.0"
                String value = boost == Math.rint(boost)
                        ? String.valueOf(boost.intValue())
                        : String.valueOf(boost);
                result.add(field + "^" + value);
            }
        });
        return result;
    }

    private static FieldValue toFieldValue(Object value) {
        if (value instanceof FieldValue fieldValue) {
            return fieldValue;
        }
        if (value instanceof String s) {
            return FieldValue.of(s);
        }
        if (value instanceof Integer i) {
            return FieldValue.of(i.longValue());
        }
        if (value instanceof Long l) {
            return FieldValue.of(l);
        }
        if (value instanceof Double d) {
            return FieldValue.of(d);
        }
        if (value instanceof Boolean b) {
            return FieldValue.of(b);
        }
        return FieldValue.of(String.valueOf(value));
    }
}
