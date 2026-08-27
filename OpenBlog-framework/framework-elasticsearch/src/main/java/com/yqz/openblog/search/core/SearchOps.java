package com.yqz.openblog.search.core;

import java.util.List;

/**
 * Elasticsearch 操作封装接口 — 内置容错，调用方无需关心 ES 连接异常。
 * <ul>
 *   <li>写操作返回 boolean，失败时返回 false</li>
 *   <li>读操作（search）失败或 ES 不可用时返回 {@link SearchResult#empty(int, int)}</li>
 *   <li>布尔操作（indexExists）失败返回 false（fail-safe）</li>
 * </ul>
 */
public interface SearchOps {

    /**
     * 索引一篇文档。失败时静默返回 false。
     *
     * @param indexName 索引名
     * @param id        文档 ID
     * @param document  文档对象
     * @return true 表示索引成功
     */
    boolean index(String indexName, String id, Object document);

    /**
     * 全文搜索。ES 不可用时返回空结果。
     *
     * @param indexName 索引名
     * @param keyword   搜索关键词
     * @param fields    搜索字段列表（支持加权，如 "title^3"）
     * @param page      页码（从 0 开始）
     * @param size      每页条数
     * @return 搜索结果（hits + 总数），失败时返回空
     */
    SearchResult search(String indexName, String keyword, List<String> fields, int page, int size);

    /**
     * 删除一篇文档。失败时静默返回 false。
     *
     * @param indexName 索引名
     * @param id        文档 ID
     * @return true 表示删除成功
     */
    boolean delete(String indexName, String id);

    /**
     * 检查索引是否存在。ES 不可用时返回 false。
     *
     * @param indexName 索引名
     * @return true 表示索引存在
     */
    boolean indexExists(String indexName);
}
