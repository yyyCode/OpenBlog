package com.yqz.openblog.search.index;

/**
 * 一个可搜索索引的定义：索引名 + 文档类型。
 *
 * <p>业务模块通过定义 {@code SearchIndex} bean 接入框架，框架不感知具体业务模型。
 * 索引名建议由 {@code openblog.search.index-prefix} + 业务名拼成，例如
 * {@code openblog_articles}。</p>
 */
public interface SearchIndex {

    /** 实际读写的 ES 索引名。 */
    String indexName();

    /** 文档类型，用于生成 mapping（读取 {@code @Field} 注解）。 */
    Class<?> documentType();
}
