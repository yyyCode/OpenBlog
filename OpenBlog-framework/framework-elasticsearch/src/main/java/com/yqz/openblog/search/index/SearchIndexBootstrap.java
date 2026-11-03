package com.yqz.openblog.search.index;

import com.yqz.openblog.search.config.SearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 应用就绪后为所有 {@link SearchIndex} bean 幂等创建索引与 mapping。
 *
 * <p>失败只打 WARN，不影响启动与业务请求。</p>
 */
public class SearchIndexBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexBootstrap.class);

    private final SearchIndexManager indexManager;
    private final SearchProperties properties;
    private final ObjectProvider<SearchIndex> indexes;

    public SearchIndexBootstrap(SearchIndexManager indexManager,
                                SearchProperties properties,
                                ObjectProvider<SearchIndex> indexes) {
        this.indexManager = indexManager;
        this.properties = properties;
        this.indexes = indexes;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.isAutoCreateIndex()) {
            return;
        }
        int ready = 0;
        int failed = 0;
        for (SearchIndex index : indexes) {
            try {
                if (indexManager.ensureIndex(index)) {
                    ready++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                // 索引初始化绝不影响应用启动
                failed++;
                log.warn("初始化 ES 索引异常（已忽略），index={}", index.indexName(), e);
            }
        }
        if (failed > 0) {
            log.warn("ES 索引初始化完成：成功 {} 个，失败 {} 个（搜索将走降级路径）", ready, failed);
        } else if (ready > 0) {
            log.info("ES 索引初始化完成：{} 个索引可用", ready);
        }
    }
}
