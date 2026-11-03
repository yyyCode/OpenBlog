package com.yqz.openblog.search.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单条搜索命中。
 *
 * @param <T> 文档类型
 */
public final class SearchHit<T> {

    private final String id;
    private final float score;
    private final T content;
    private final Map<String, List<String>> highlights;

    public SearchHit(String id, float score, T content, Map<String, List<String>> highlights) {
        this.id = id;
        this.score = score;
        this.content = content;
        this.highlights = highlights == null ? Collections.emptyMap() : highlights;
    }

    /** ES 文档 ID（即写入时传入的业务 ID）。 */
    public String getId() {
        return id;
    }

    public float getScore() {
        return score;
    }

    /** 反序列化后的文档；索引中无该字段时为 null。 */
    public T getContent() {
        return content;
    }

    /** 高亮字段（当前 SearchRequest 未开启高亮，保留给自定义查询使用）。 */
    public Map<String, List<String>> getHighlights() {
        return highlights;
    }
}
