package com.yqz.openblog.search.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

/**
 * {@link SearchIndexManager} 的默认实现，基于 Spring Data ES 的 {@link IndexOperations}。
 */
public class DefaultSearchIndexManager implements SearchIndexManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultSearchIndexManager.class);

    private final ElasticsearchOperations esOps;

    public DefaultSearchIndexManager(ElasticsearchOperations esOps) {
        this.esOps = esOps;
    }

    @Override
    public boolean ensureIndex(SearchIndex index) {
        IndexCoordinates coordinates = IndexCoordinates.of(index.indexName());
        try {
            IndexOperations ops = esOps.indexOps(coordinates);
            if (!ops.exists()) {
                ops.create();
                log.info("已创建 ES 索引 {}", index.indexName());
            }
            Document mapping = ops.createMapping(index.documentType());
            if (mapping != null && !mapping.isEmpty()) {
                // 幂等：已存在的字段不会被覆盖，新增字段会被补齐
                ops.putMapping(mapping);
            }
            return true;
        } catch (Exception e) {
            log.warn("确保 ES 索引失败（已忽略），index={}", index.indexName(), e);
            return false;
        }
    }

    @Override
    public boolean indexExists(SearchIndex index) {
        try {
            return esOps.indexOps(IndexCoordinates.of(index.indexName())).exists();
        } catch (Exception e) {
            log.warn("检查 ES 索引失败（按不存在处理），index={}", index.indexName(), e);
            return false;
        }
    }
}
