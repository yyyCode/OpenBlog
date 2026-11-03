package com.yqz.openblog.search.config;

import com.yqz.openblog.search.document.ArticleDocument;
import com.yqz.openblog.search.index.SearchIndex;
import com.yqz.openblog.search.index.SimpleSearchIndex;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文章搜索索引定义。
 *
 * <p>索引名走配置前缀，多环境（如测试环境共用集群）可通过
 * {@code openblog.search.index-prefix} 隔离。</p>
 */
@Configuration
public class ArticleSearchIndexConfig {

    /** 文章索引的业务名。 */
    public static final String ARTICLE_INDEX_BUSINESS_NAME = "articles";

    @Bean
    public SearchIndex articleSearchIndex(SearchProperties properties) {
        return new SimpleSearchIndex(
                properties.getIndexPrefix() + ARTICLE_INDEX_BUSINESS_NAME,
                ArticleDocument.class);
    }
}
