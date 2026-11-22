package com.yqz.openblog.search.index;

/**
 * 索引生命周期管理：确保索引及其 mapping 存在。
 *
 * <p>所有方法内置容错，ES 不可用时返回 false 而非抛异常。</p>
 */
public interface SearchIndexManager {

    /**
     * 幂等确保索引存在：不存在则创建并写入 mapping，已存在则补齐 mapping。
     *
     * @return true 表示索引可用
     */
    boolean ensureIndex(SearchIndex index);

    /**
     * 删除并重建索引（含 mapping），供全量重建索引用。
     *
     * <p>注意：重建期间该索引的搜索结果为空，调用方应只在管理端操作中使用。</p>
     *
     * @return true 表示索引已重建成功
     */
    boolean recreateIndex(SearchIndex index);

    /**
     * 检查索引是否存在（ES 不可用时返回 false）。
     */
    boolean indexExists(SearchIndex index);
}
