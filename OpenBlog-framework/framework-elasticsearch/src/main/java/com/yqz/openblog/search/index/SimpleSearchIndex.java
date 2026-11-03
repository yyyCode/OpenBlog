package com.yqz.openblog.search.index;

import java.util.Objects;

/**
 * {@link SearchIndex} 的默认不可变实现。
 */
public final class SimpleSearchIndex implements SearchIndex {

    private final String indexName;
    private final Class<?> documentType;

    public SimpleSearchIndex(String indexName, Class<?> documentType) {
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName must not be blank");
        }
        this.indexName = indexName;
        this.documentType = Objects.requireNonNull(documentType, "documentType");
    }

    @Override
    public String indexName() {
        return indexName;
    }

    @Override
    public Class<?> documentType() {
        return documentType;
    }

    @Override
    public String toString() {
        return "SearchIndex{" + indexName + ", " + documentType.getSimpleName() + '}';
    }
}
