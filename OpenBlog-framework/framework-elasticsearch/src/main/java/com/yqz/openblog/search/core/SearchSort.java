package com.yqz.openblog.search.core;

/**
 * 搜索排序字段。
 */
public final class SearchSort {

    public enum Direction {
        ASC, DESC
    }

    private final String field;
    private final Direction direction;

    private SearchSort(String field, Direction direction) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("sort field must not be blank");
        }
        this.field = field.trim();
        this.direction = direction;
    }

    public static SearchSort asc(String field) {
        return new SearchSort(field, Direction.ASC);
    }

    public static SearchSort desc(String field) {
        return new SearchSort(field, Direction.DESC);
    }

    public String getField() {
        return field;
    }

    public Direction getDirection() {
        return direction;
    }
}
