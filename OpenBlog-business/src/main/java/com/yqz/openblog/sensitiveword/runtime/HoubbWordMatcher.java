package com.yqz.openblog.sensitiveword.runtime;

import com.github.houbb.sensitive.word.api.IWordResult;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.github.houbb.sensitive.word.support.result.WordResultHandlers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        return firstHit(text) != null;
    }

    @Override
    public HitRange firstHit(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        List<IWordResult> raw = delegate.findAll(text, WordResultHandlers.raw());
        if (raw.isEmpty()) {
            return null;
        }
        IWordResult first = raw.get(0);
        return new HitRange(first.startIndex(), first.endIndex());
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
        Set<String> words = new LinkedHashSet<>();
        for (IWordResult r : raw) {
            ranges.add(new HitRange(r.startIndex(), r.endIndex()));
            // 与 Trie/AC 一致：hitWords 是原文片段，不是归一化后的词典词（大小写/全角场景会不同）
            words.add(text.substring(r.startIndex(), Math.min(r.endIndex(), text.length())));
        }
        return new FilterResult(true, delegate.replace(text), List.copyOf(words), List.copyOf(ranges));
    }
}
