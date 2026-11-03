package com.yqz.openblog.search.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 声明式搜索请求。
 *
 * <p>只覆盖项目常见场景：关键词多字段检索 + term 精确过滤 + 排序 + 分页。
 * 更复杂的查询请使用 {@link SearchOps#search(com.yqz.openblog.search.index.SearchIndex,
 * org.springframework.data.elasticsearch.core.query.Query, Class)} 传入自定义 {@code NativeQuery}。</p>
 */
public final class SearchRequest {

    private final String keyword;
    /** 字段名 → 权重；权重为 null 表示不加权。 */
    private final Map<String, Float> fields;
    private final Map<String, Object> termFilters;
    private final List<SearchSort> sorts;
    private final int page;
    private final int size;

    private SearchRequest(Builder builder) {
        this.keyword = builder.keyword == null ? "" : builder.keyword.trim();
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.fields));
        this.termFilters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.termFilters));
        this.sorts = Collections.unmodifiableList(new ArrayList<>(builder.sorts));
        this.page = builder.page;
        this.size = builder.size;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getKeyword() {
        return keyword;
    }

    /** 是否有关键词；无关键词时退化为 matchAll + 过滤。 */
    public boolean hasKeyword() {
        return !keyword.isEmpty();
    }

    public Map<String, Float> getFields() {
        return fields;
    }

    public Map<String, Object> getTermFilters() {
        return termFilters;
    }

    public List<SearchSort> getSorts() {
        return sorts;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public static final class Builder {

        private String keyword;
        private final Map<String, Float> fields = new LinkedHashMap<>();
        private final Map<String, Object> termFilters = new LinkedHashMap<>();
        private final List<SearchSort> sorts = new ArrayList<>();
        private int page = 0;
        private int size = 20;

        private Builder() {
        }

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        /**
         * 增加一个参与多字段匹配的字段。
         *
         * @param field 字段名（不带权重后缀）
         * @param boost 权重，null 或 ≤0 表示不加权
         */
        public Builder field(String field, Float boost) {
            this.fields.put(field, boost);
            return this;
        }

        /** 精确过滤（term），如状态、分类 ID。 */
        public Builder termFilter(String field, Object value) {
            this.termFilters.put(field, value);
            return this;
        }

        public Builder sort(SearchSort sort) {
            this.sorts.add(sort);
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public SearchRequest build() {
            if (page < 0) {
                throw new IllegalArgumentException("page must be >= 0");
            }
            if (size <= 0) {
                throw new IllegalArgumentException("size must be > 0");
            }
            // 有关键词却未声明字段属于编程错误，与"ES 故障"区分开
            if (keyword != null && !keyword.isBlank() && fields.isEmpty()) {
                throw new IllegalArgumentException("search fields must not be empty when keyword is present");
            }
            return new SearchRequest(this);
        }
    }
}
