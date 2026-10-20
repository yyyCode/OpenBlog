package com.yqz.openblog.sensitiveword.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrieWordMatcherTest {

    private static TrieWordMatcher matcher(String... words) {
        return TrieWordMatcher.build(List.of(words), CharNormalizer.houbbCompatible());
    }

    @Test
    void overlappingWords_longestWins() {
        TrieWordMatcher m = matcher("敏感", "敏感词");
        FilterResult r = m.filter("这是敏感词");
        assertTrue(r.hit());
        assertEquals("这是***", r.filteredText());
        assertEquals(List.of("敏感词"), r.hitWords());
    }

    @Test
    void noHit_returnsOriginal() {
        TrieWordMatcher m = matcher("广告");
        FilterResult r = m.filter("这是一段正常文字");
        assertFalse(r.hit());
        assertEquals("这是一段正常文字", r.filteredText());
        assertFalse(m.contains("这是一段正常文字"));
    }

    @Test
    void nullAndEmpty() {
        TrieWordMatcher m = matcher("广告");
        assertFalse(m.contains(null));
        assertFalse(m.contains(""));
        assertEquals("", m.filter(null).filteredText());
    }

    @Test
    void normalization_fullWidthAndCase() {
        TrieWordMatcher m = matcher("abc");
        assertTrue(m.contains("ＡＢＣ"));
        assertTrue(m.contains("ABC"));
        assertEquals("***", m.filter("ＡＢＣ").filteredText());
    }

    @Test
    void repeatedStart_onlyLeftmostMatchKept() {
        TrieWordMatcher m = matcher("哈哈");
        FilterResult r = m.filter("哈哈哈");
        assertEquals("**哈", r.filteredText());
        assertEquals(1, r.ranges().size());
    }

    @Test
    void englishWordBoundary_matchesHoubbSemantics() {
        TrieWordMatcher m = matcher("ass");
        assertFalse(m.contains("class"));
        assertTrue(m.contains("ass"));
        assertTrue(m.contains("an ass"));
        assertEquals("an ***", m.filter("an ass").filteredText());
    }
}
