package com.yqz.openblog.search.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 全文搜索结果封装。
 */
public class SearchResult {

    private final List<Map<String, Object>> hits;
    private final long totalHits;
    private final int page;
    private final int size;

    public SearchResult(List<Map<String, Object>> hits, long totalHits, int page, int size) {
        this.hits = hits;
        this.totalHits = totalHits;
        this.page = page;
        this.size = size;
    }

    /**
     * ES 不可用或查询异常时的空结果。
     */
    public static SearchResult empty(int page, int size) {
        return new SearchResult(Collections.emptyList(), 0, page, size);
    }

    public List<Map<String, Object>> getHits() {
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
}
