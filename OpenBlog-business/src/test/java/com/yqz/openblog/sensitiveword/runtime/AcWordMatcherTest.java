package com.yqz.openblog.sensitiveword.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcWordMatcherTest {

    private static AcWordMatcher matcher(String... words) {
        return AcWordMatcher.build(List.of(words), CharNormalizer.houbbCompatible());
    }

    @Test
    void suffixWord_foundViaOutputLink() {
        AcWordMatcher m = matcher("abc", "bc");
        FilterResult r = m.filter("abc");
        assertTrue(r.hit());
        assertEquals("***", r.filteredText());
        // "bc" 左侧紧邻英文字母，按 houbb 的英文词边界规则不算命中
        assertEquals(List.of("abc"), r.hitWords());
    }

    @Test
    void failTransition_findsWordAfterMismatch() {
        AcWordMatcher m = matcher("甲乙丙丁", "乙丙");
        assertTrue(m.contains("x乙丙"));
        assertTrue(m.contains("甲乙x甲乙丙丁"));
        assertFalse(m.contains("甲乙x"));
    }

    @Test
    void overlappingWords_longestWins() {
        AcWordMatcher m = matcher("敏感", "敏感词");
        FilterResult r = m.filter("这是敏感词");
        assertEquals("这是***", r.filteredText());
        assertEquals(List.of("敏感词"), r.hitWords());
    }

    @Test
    void singleCharWord() {
        AcWordMatcher m = matcher("禁");
        assertTrue(m.contains("违禁内容"));
        assertEquals("违*内容", m.filter("违禁内容").filteredText());
    }

    @Test
    void nullAndEmpty() {
        AcWordMatcher m = matcher("广告");
        assertFalse(m.contains(null));
        assertFalse(m.contains(""));
        assertEquals("", m.filter(null).filteredText());
    }

    @Test
    void normalization_fullWidthAndCase() {
        AcWordMatcher m = matcher("abc");
        assertTrue(m.contains("ＡＢＣ"));
        assertEquals("***", m.filter("ABC").filteredText());
    }

    @Test
    void englishWordBoundary_matchesHoubbSemantics() {
        AcWordMatcher m = matcher("ass");
        assertFalse(m.contains("class"));
        assertTrue(m.contains("ass"));
        assertTrue(m.contains("an ass"));
        assertEquals("an ***", m.filter("an ass").filteredText());
    }
}
