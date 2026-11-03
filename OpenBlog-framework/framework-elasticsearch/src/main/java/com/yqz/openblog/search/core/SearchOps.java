package com.yqz.openblog.search.core;

import com.yqz.openblog.search.index.SearchIndex;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.Map;

/**
 * Elasticsearch 操作封装 — 内置容错，调用方无需关心 ES 连接异常。
 *
 * <ul>
 *   <li>写操作返回 boolean，失败时返回 false，不抛异常；</li>
 *   <li>搜索失败返回 {@link SearchResult#degraded(int, int)}，正常无结果返回
 *       {@link SearchResult#empty(int, int)}；</li>
 *   <li>参数错误（如有关键词却没声明字段）属于编程错误，直接抛
 *       {@link IllegalArgumentException}，不与 ES 故障混淆。</li>
 * </ul>
 */
public interface SearchOps {

    /**
     * 索引一篇文档。失败时静默返回 false。
     *
     * @param index    索引定义
     * @param id       文档 ID（业务主键）
     * @param document 文档对象
     * @return true 表示索引成功
     */
    <T> boolean index(SearchIndex index, String id, T document);

    /**
     * 批量索引文档。失败时静默返回 false。
     *
     * @param index          索引定义
     * @param documentsById  ID → 文档
     * @return true 表示全部写入成功
     */
    <T> boolean indexBatch(SearchIndex index, Map<String, T> documentsById);

    /**
     * 删除一篇文档。失败时静默返回 false（含文档不存在）。
     */
    boolean delete(SearchIndex index, String id);

    /**
     * 声明式全文搜索。
     *
     * @param index   索引定义
     * @param request 搜索请求
     * @param type    文档类型
     * @return 搜索结果；ES 不可用时 {@code degraded=true}
     */
    <T> SearchResult<T> search(SearchIndex index, SearchRequest request, Class<T> type);

    /**
     * 自定义查询扩展：传 Spring Data {@code Query}（通常是 {@code NativeQuery}）。
     *
     * @param index 索引定义
     * @param query 自定义查询，需自带分页
     * @param type  文档类型
     * @return 搜索结果；ES 不可用时 {@code degraded=true}
     */
    <T> SearchResult<T> search(SearchIndex index, Query query, Class<T> type);
}
