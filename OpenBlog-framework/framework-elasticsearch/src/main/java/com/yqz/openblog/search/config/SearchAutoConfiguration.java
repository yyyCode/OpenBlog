package com.yqz.openblog.search.config;

import com.yqz.openblog.search.core.DefaultSearchOps;
import com.yqz.openblog.search.core.SearchOps;
import com.yqz.openblog.search.index.DefaultSearchIndexManager;
import com.yqz.openblog.search.index.SearchIndex;
import com.yqz.openblog.search.index.SearchIndexBootstrap;
import com.yqz.openblog.search.index.SearchIndexManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/**
 * 搜索框架自动装配。
 *
 * <p>{@link SearchProperties} 始终注册（仅依赖 ES 类存在），业务可以无条件注入；
 * 其余组件仅在 {@code openblog.search.enabled=true} 时注册。</p>
 */
@AutoConfiguration
@ConditionalOnClass(ElasticsearchOperations.class)
@EnableConfigurationProperties(SearchProperties.class)
public class SearchAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "openblog.search", name = "enabled", havingValue = "true")
    public SearchOps searchOps(ElasticsearchOperations elasticsearchOperations) {
        return new DefaultSearchOps(elasticsearchOperations);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "openblog.search", name = "enabled", havingValue = "true")
    public SearchIndexManager searchIndexManager(ElasticsearchOperations elasticsearchOperations) {
        return new DefaultSearchIndexManager(elasticsearchOperations);
    }

    @Bean
    @ConditionalOnProperty(prefix = "openblog.search", name = "enabled", havingValue = "true")
    public SearchIndexBootstrap searchIndexBootstrap(SearchIndexManager searchIndexManager,
                                                     SearchProperties properties,
                                                     ObjectProvider<SearchIndex> indexes) {
        return new SearchIndexBootstrap(searchIndexManager, properties, indexes);
    }
}
