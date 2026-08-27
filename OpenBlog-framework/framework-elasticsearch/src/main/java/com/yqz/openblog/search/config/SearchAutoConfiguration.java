package com.yqz.openblog.search.config;

import com.yqz.openblog.search.core.DefaultSearchOps;
import com.yqz.openblog.search.core.SearchOps;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

@AutoConfiguration
@ConditionalOnClass(ElasticsearchOperations.class)
@ConditionalOnProperty(name = "openblog.search.enabled", havingValue = "true")
@EnableConfigurationProperties(SearchProperties.class)
public class SearchAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SearchOps searchOps(ElasticsearchOperations elasticsearchOperations) {
        return new DefaultSearchOps(elasticsearchOperations);
    }
}
