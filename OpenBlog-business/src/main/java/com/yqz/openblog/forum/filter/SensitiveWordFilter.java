package com.yqz.openblog.forum.filter;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.github.houbb.sensitive.word.support.deny.WordDenys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 敏感词过滤器 — 基于 DFA 算法，使用 {@code com.github.houbb:sensitive-word}。
 * <p>
 * 主要用法：
 * <ul>
 *   <li>{@link #contains(String)} — 是否包含敏感词</li>
 *   <li>{@link #findAll(String)} — 返回所有命中的敏感词</li>
 *   <li>{@link #replace(String)} — 用 {@code *} 替换敏感词</li>
 * </ul>
 */
@Component
public class SensitiveWordFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilter.class);

    private final SensitiveWordBs sw;

    public SensitiveWordFilter() {
        // 使用内置词库（6W+），后续可扩展从 DB/文件加载自定义词库
        this.sw = SensitiveWordBs.newInstance()
                .wordDeny(WordDenys.defaults())
                .wordAllow(WordAllows.defaults())
                .ignoreCase(true)
                .ignoreWidth(true)
                .ignoreNumStyle(true)
                .ignoreChineseStyle(true)
                .ignoreEnglishStyle(true)
                .ignoreRepeat(false)
                .init();
        log.info("SensitiveWordFilter initialized");
    }

    /**
     * 是否包含敏感词。
     */
    public boolean contains(String text) {
        if (text == null || text.isBlank()) return false;
        return sw.contains(text);
    }

    /**
     * 返回命中的所有敏感词。
     */
    public List<String> findAll(String text) {
        if (text == null || text.isBlank()) return List.of();
        return sw.findAll(text);
    }

    /**
     * 用 {@code *} 替换敏感词。
     */
    public String replace(String text) {
        if (text == null || text.isBlank()) return text;
        return sw.replace(text);
    }
}
