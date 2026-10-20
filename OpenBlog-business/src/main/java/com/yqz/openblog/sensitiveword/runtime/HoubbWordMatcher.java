package com.yqz.openblog.sensitiveword.runtime;

import com.github.houbb.sensitive.word.api.IWordResult;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.github.houbb.sensitive.word.support.result.WordResultHandlers;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产基线：直接封装 houbb DFA。
 * <p>
 * 词表来自版本快照（不再使用 jar 内置 deny 词库）；内置 allow 表保留，避免迁移后出现新的误伤。
 */
public final class HoubbWordMatcher implements WordMatcher {

    private final SensitiveWordBs delegate;

    private HoubbWordMatcher(SensitiveWordBs delegate) {
        this.delegate = delegate;
    }

    public static HoubbWordMatcher build(List<String> words) {
        return new HoubbWordMatcher(SensitiveWordBs.newInstance()
                .wordDeny(() -> words)
                .wordAllow(WordAllows.defaults())
                .ignoreCase(true)
                .ignoreWidth(true)
                .ignoreNumStyle(true)
                .ignoreChineseStyle(true)
                .ignoreEnglishStyle(true)
                .ignoreRepeat(false)
                // 每个起点返回最长命中，而不是遇短词即停；与 Trie / AC 的区间语义对齐
                .wordFailFast(false)
                .init());
    }

    @Override
    public String engine() {
        return "houbb";
    }

    @Override
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // 库内部走 FAIL_FAST，从 0 开始找到第一个命中即停，避免 findAll 扫完整段文本
        return delegate.contains(text);
    }

    @Override
    public HitRange firstHit(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        // FAIL_FAST：只取最左侧命中，不再像 findAll 那样收集全文所有结果
        IWordResult first = delegate.findFirst(text, WordResultHandlers.raw());
        return first == null ? null : new HitRange(first.startIndex(), first.endIndex());
    }

    @Override
    public FilterResult filter(String text) {
        if (text == null || text.isEmpty()) {
            return FilterResult.empty(text);
        }
        List<IWordResult> raw = delegate.findAll(text, WordResultHandlers.raw());
        if (raw.isEmpty()) {
            return FilterResult.empty(text);
        }
        List<HitRange> ranges = new ArrayList<>(raw.size());
        for (IWordResult r : raw) {
            ranges.add(new HitRange(r.startIndex(), r.endIndex()));
        }
        // 复用统一收敛逻辑；不再调用 delegate.replace（其内部会再跑一遍 findAll）
        return WordMatcher.buildResult(text.toCharArray(), ranges);
    }
}
