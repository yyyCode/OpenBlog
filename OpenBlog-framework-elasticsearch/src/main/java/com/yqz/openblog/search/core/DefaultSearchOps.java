package com.yqz.openblog.search.core;

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public boolean index(String indexName, String id, Object document) {
        try {
            IndexQuery query = new IndexQueryBuilder()
                    .withId(id)
                    .withObject(document)
                    .build();
            esOps.index(query, IndexCoordinates.of(indexName));
            return true;
        } catch (Exception e) {
            log.warn("ES index 失败，index={}, id={}", indexName, id, e);
            return false;
        }
    }

    @Override
    public SearchResult search(String indexName, String keyword, List<String> fields, int page, int size) {
        try {
            // 去掉字段名中的权重后缀（如 "title^3" → "title"），ES Java Client 的 multiMatch 不支持内联权重
            List<String> cleanFields = fields.stream()
                    .map(f -> f.contains("^") ? f.substring(0, f.indexOf('^')) : f)
                    .collect(Collectors.toList());

            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.multiMatch(mm -> mm
                            .query(keyword)
                            .fields(cleanFields)
                            .type(TextQueryType.BestFields)
                    ))
                    .withPageable(PageRequest.of(page, size))
                    .build();

            SearchHits<Map> searchHits = esOps.search(query, Map.class, IndexCoordinates.of(indexName));

            List<Map<String, Object>> hits = new ArrayList<>();
            for (SearchHit<Map> hit : searchHits.getSearchHits()) {
                Map<String, Object> source = hit.getContent();
                if (source != null) {
                    hits.add(source);
                }
            }

            return new SearchResult(hits, searchHits.getTotalHits(), page, size);
        } catch (Exception e) {
            log.warn("ES search 失败（已降级），index={}, keyword={}", indexName, keyword, e);
            return SearchResult.empty(page, size);
        }
    }

    @Override
    public boolean delete(String indexName, String id) {
        try {
            esOps.delete(id, IndexCoordinates.of(indexName));
            return true;
        } catch (Exception e) {
            log.warn("ES delete 失败（已忽略），index={}, id={}", indexName, id, e);
            return false;
        }
    }

    @Override
    public boolean indexExists(String indexName) {
        try {
            return esOps.indexOps(IndexCoordinates.of(indexName)).exists();
        } catch (Exception e) {
            log.warn("ES indexExists 失败，index={}", indexName, e);
            return false;
        }
    }
}
