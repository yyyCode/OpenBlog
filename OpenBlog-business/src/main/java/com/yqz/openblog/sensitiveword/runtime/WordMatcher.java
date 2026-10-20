package com.yqz.openblog.sensitiveword.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 敏感词匹配器统一接口。
 * <p>
 * 三种实现（houbb / trie / ac）必须满足同一套匹配语义：
 * 从左到右选取命中区间，每个起点取最长命中，区间之间不重叠（与 houbb 的 replace 行为一致）；
 * 替换为等长 {@code *}；词表与文本使用同一套归一化。
 */
public interface WordMatcher {

    /** 全量过滤。 */
    FilterResult filter(String text);

    /** 只看是否命中，实现应走提前返回的快路径。 */
    boolean contains(String text);

    /**
     * 第一个命中区间（原文下标，[start, endExclusive)）；未命中返回 null。
     * <p>
     * 命中日志只需要一个片段，用它替代「contains + filter」的二次全量匹配。
     */
    HitRange firstHit(String text);

    /** 用等长 {@code *} 替换命中区间。 */
    default String replace(String text) {
        return filter(text).filteredText();
    }

    /** 引擎名：houbb / trie / ac。 */
    String engine();

    /**
     * 复刻 houbb 默认的英文词边界条件（{@code WordResultConditionEnglishWordMatch}）：
     * 纯英文/空格的命中词，左右不能再紧邻英文字母，避免把 {@code class} 里的 {@code ass} 判成命中。
     * Trie / AC 必须在每次候选命中后过一遍，才能与 houbb 引擎行为一致。
     */
    static boolean acceptsMatch(char[] text, int start, int endExclusive) {
        boolean englishOrSpace = true;
        for (int i = start; i < endExclusive; i++) {
            char c = text[i];
            if (!isEnglish(c) && !isSpace(c)) {
                englishOrSpace = false;
                break;
            }
        }
        if (!englishOrSpace) {
            return true;
        }
        if (start > 0 && isEnglish(text[start - 1])) {
            return false;
        }
        return endExclusive >= text.length || !isEnglish(text[endExclusive]);
    }

    private static boolean isEnglish(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /** 与 houbb 的 CharUtil.isSpace 对齐。 */
    private static boolean isSpace(char c) {
        return Character.isSpaceChar(c) || c == 19;
    }

    /**
     * 把「所有候选命中」收敛为 houbb 的最终结果：按起点升序、同起点取最长，
     * 已选区间之后的起点直接跳过，因此重叠词只会保留最左侧、最长的那一个。
     */
    static FilterResult buildResult(char[] src, List<HitRange> candidates) {
        if (candidates.isEmpty()) {
            return FilterResult.empty(new String(src));
        }
        List<HitRange> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(HitRange::start)
                .thenComparing(Comparator.comparingInt(HitRange::endExclusive).reversed()));

        boolean[] marked = new boolean[src.length];
        List<HitRange> selected = new ArrayList<>();
        LinkedHashSet<String> words = new LinkedHashSet<>();
        int cursor = 0;
        for (HitRange candidate : sorted) {
            if (candidate.start() < cursor) {
                continue;
            }
            selected.add(candidate);
            words.add(new String(src, candidate.start(), candidate.endExclusive() - candidate.start()));
            Arrays.fill(marked, candidate.start(), candidate.endExclusive(), true);
            cursor = candidate.endExclusive();
        }

        char[] out = src.clone();
        for (int i = 0; i < marked.length; i++) {
            if (marked[i]) {
                out[i] = '*';
            }
        }
        return new FilterResult(!selected.isEmpty(), new String(out), List.copyOf(words), List.copyOf(selected));
    }
}
