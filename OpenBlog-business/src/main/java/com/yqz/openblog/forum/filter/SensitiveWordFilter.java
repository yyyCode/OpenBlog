package com.yqz.openblog.forum.filter;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.github.houbb.sensitive.word.support.deny.WordDenys;
import com.yqz.openblog.config.SensitiveWordProperties;
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
 * <p>
 * 内置词库（6W+）是通用词表，必然带误伤：正常词被单独收录，正常语境下也会拦下来
 * （例：「长期」在词库里是一条裸词，同段落邻居是「找长期小姐」「长期出售手枪」这类垃圾短语）。
 * 词库自带的 allow 表只有十来条，兜不住，故白名单由
 * {@code openblog.sensitive-word.allow-words} 配置补充；白名单在 {@code init()} 时并入 DFA，
 * 改动需重启生效。命中会打 WARN 日志（命中词 + 局部上下文），事后按日志补白名单即可。
 */
@Component
public class SensitiveWordFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilter.class);

    /** 命中日志里上下文片段前后各留的字数。 */
    private static final int CONTEXT_PADDING = 12;

    private final SensitiveWordBs sw;

    public SensitiveWordFilter(SensitiveWordProperties properties) {
        List<String> allowWords = properties.getAllowWords() == null ? List.of() : properties.getAllowWords();
        // 内置词库 + 配置白名单叠加：白名单是「命中词库但属误伤」的唯一出口
        this.sw = SensitiveWordBs.newInstance()
                .wordDeny(WordDenys.defaults())
                .wordAllow(WordAllows.chains(WordAllows.defaults(), () -> allowWords))
                .ignoreCase(true)
                .ignoreWidth(true)
                .ignoreNumStyle(true)
                .ignoreChineseStyle(true)
                .ignoreEnglishStyle(true)
                .ignoreRepeat(false)
                .init();
        log.info("SensitiveWordFilter initialized, allowWords={}", allowWords);
    }

    /**
     * 是否包含敏感词。命中时打一条 WARN（命中词 + 局部上下文）——只记录片段不记录全文。
     */
    public boolean contains(String text) {
        if (text == null || text.isBlank()) return false;
        if (!sw.contains(text)) return false;
        List<String> words = findAll(text).stream().distinct().toList();
        log.warn("敏感词命中 words={} context={}", words, context(text, words));
        return true;
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

    /** 取首个命中词前后各 {@value #CONTEXT_PADDING} 字、空白压成单空格；无命中返回空串。 */
    private String context(String text, List<String> words) {
        if (words.isEmpty()) return "";
        String first = words.get(0);
        int idx = text.indexOf(first);
        if (idx < 0) return "";
        int start = Math.max(0, idx - CONTEXT_PADDING);
        int end = Math.min(text.length(), idx + first.length() + CONTEXT_PADDING);
        return text.substring(start, end).replaceAll("\\s+", " ");
    }
}
