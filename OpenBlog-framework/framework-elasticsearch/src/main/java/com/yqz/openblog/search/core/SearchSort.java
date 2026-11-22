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
    private final boolean score;

    private SearchSort(String field, Direction direction, boolean score) {
        if (!score && (field == null || field.isBlank())) {
            throw new IllegalArgumentException("sort field must not be blank");
        }
        this.field = field == null ? null : field.trim();
        this.direction = direction;
        this.score = score;
    }

    public static SearchSort asc(String field) {
        return new SearchSort(field, Direction.ASC, false);
    }

    public static SearchSort desc(String field) {
        return new SearchSort(field, Direction.DESC, false);
    }

    /**
     * 按相关性得分排序。
     *
     * <p>注意：一旦请求里出现字段排序，ES 默认不再计算得分；把本排序放在第一位即可
     * 先按相关性、再按时间等次级字段排序。</p>
     */
    public static SearchSort score(Direction direction) {
        return new SearchSort(null, direction, true);
    }

    /** 相关性降序，等价于 {@code score(Direction.DESC)}。 */
    public static SearchSort relevance() {
        return score(Direction.DESC);
    }

    public String getField() {
        return field;
    }

    public Direction getDirection() {
        return direction;
    }

    /** 是否为相关性得分排序（{@link #getField()} 此时为 null）。 */
    public boolean isScore() {
        return score;
    }
}
