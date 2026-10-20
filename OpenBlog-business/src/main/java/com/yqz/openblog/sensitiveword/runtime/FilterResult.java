package com.yqz.openblog.sensitiveword.runtime;

import java.util.List;

/**
 * 一次过滤的结果。
 *
 * @param hit          是否命中
 * @param filteredText 命中区间替换为等长 {@code *} 后的文本；未命中时与原文本相同
 * @param hitWords     原文中的命中片段（去重，保留首次出现顺序）
 * @param ranges       命中区间，供一致性测试与调试使用
 */
public record FilterResult(
        boolean hit,
        String filteredText,
        List<String> hitWords,
        List<HitRange> ranges) {

    public static FilterResult empty(String original) {
        return new FilterResult(false, original == null ? "" : original, List.of(), List.of());
    }
}
