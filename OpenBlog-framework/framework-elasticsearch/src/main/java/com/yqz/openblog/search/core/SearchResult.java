package com.yqz.openblog.search.core;

import java.util.Collections;
import java.util.List;

/**
 * 搜索结果。
 *
 * <p>{@code degraded=true} 表示 ES 不可用或查询异常，调用方应降级到数据库查询；
 * 正常但无结果时 {@code degraded=false}、{@code totalHits=0}。</p>
 *
 * @param <T> 文档类型
 */
public final class SearchResult<T> {

    private final List<SearchHit<T>> hits;
    private final long totalHits;
    private final int page;
    private final int size;
    private final boolean degraded;

    public SearchResult(List<SearchHit<T>> hits, long totalHits, int page, int size, boolean degraded) {
        this.hits = hits == null ? Collections.emptyList() : hits;
        this.totalHits = totalHits;
        this.page = page;
        this.size = size;
        this.degraded = degraded;
    }

    /** 查询成功但没有命中。 */
    public static <T> SearchResult<T> empty(int page, int size) {
        return new SearchResult<>(Collections.emptyList(), 0, page, size, false);
    }

    /** ES 不可用/查询失败，调用方应走数据库降级路径。 */
    public static <T> SearchResult<T> degraded(int page, int size) {
        return new SearchResult<>(Collections.emptyList(), 0, page, size, true);
    }

    public List<SearchHit<T>> getHits() {
        return hits;
    }

    public long getTotalHits() {
        return totalHits;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public boolean isDegraded() {
        return degraded;
    }
}
