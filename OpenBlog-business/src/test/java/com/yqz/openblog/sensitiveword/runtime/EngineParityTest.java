package com.yqz.openblog.sensitiveword.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三引擎与固定语料的一致性对照：命中布尔、过滤后文本必须完全一致。 */
class EngineParityTest {

    private static final List<String> WORDS = List.of(
            "a", "ab", "abc", "bc", "bcd", "敏感", "敏感词", "感词", "甲乙", "乙丙", "测试");

    private static final List<String> TEXTS = List.of(
            "",
            "普通文本",
            "abc",
            "xabcx",
            "敏感词测试",
            "ＡＢＣ",
            "AbC甲乙丙",
            "aabbcc",
            "敏感敏感词感词",
            "ab",
            "a b",
            "abc abc",
            "AbC sensitive",
            "ＡＢＣ",
            "ＡＢＣabc甲乙",
            "班级里的 ass 不是脏话");

    @Test
    void fixedCorpus_allEnginesAgree() {
        assertAgree(WORDS, TEXTS);
    }

    @Test
    void randomCorpus_allEnginesAgree() {
        String alphabet = "abc甲乙丙敏感词测试";
        Random random = new Random(42);
        List<String> words = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            int len = 1 + random.nextInt(3);
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < len; j++) {
                sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            words.add(sb.toString());
        }
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            int len = random.nextInt(40);
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < len; j++) {
                sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            texts.add(sb.toString());
        }
        assertAgree(words, texts);
    }

    private void assertAgree(List<String> words, List<String> texts) {
        CharNormalizer normalizer = CharNormalizer.houbbCompatible();
        WordMatcher houbb = HoubbWordMatcher.build(words);
        WordMatcher trie = TrieWordMatcher.build(words, normalizer);
        WordMatcher ac = AcWordMatcher.build(words, normalizer);

        for (String text : texts) {
            FilterResult h = houbb.filter(text);
            FilterResult t = trie.filter(text);
            FilterResult a = ac.filter(text);

            assertEquals(h.hit(), t.hit(), "trie hit differs: " + text);
            assertEquals(h.hit(), a.hit(), "ac hit differs: " + text);
            assertEquals(h.filteredText(), t.filteredText(), "trie text differs: " + text);
            assertEquals(h.filteredText(), a.filteredText(), "ac text differs: " + text);
            assertEquals(rangeSet(t), rangeSet(a), "range differs: " + text);
            assertEquals(new HashSet<>(h.hitWords()), new HashSet<>(t.hitWords()), "trie words differ: " + text);
            assertEquals(new HashSet<>(h.hitWords()), new HashSet<>(a.hitWords()), "ac words differ: " + text);
            assertEquals(houbb.contains(text), trie.contains(text), "trie contains differs: " + text);
            assertEquals(houbb.contains(text), ac.contains(text), "ac contains differs: " + text);
        }
    }

    private static Set<String> rangeSet(FilterResult r) {
        Set<String> set = new HashSet<>();
        for (HitRange range : r.ranges()) {
            set.add(range.start() + ":" + range.endExclusive());
        }
        return set;
    }
}
