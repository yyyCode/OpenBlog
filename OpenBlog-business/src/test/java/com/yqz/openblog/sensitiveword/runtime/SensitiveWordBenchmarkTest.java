package com.yqz.openblog.sensitiveword.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 三引擎基准对比（默认跳过）：
 * <pre>
 * mvn -pl OpenBlog-business -am test -Dopenblog.bench=true \
 *     -Dtest=SensitiveWordBenchmarkTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 * 关闭 fail-fast 保证三方都跑完；结果只打印，不做断言。
 */
@EnabledIfSystemProperty(named = "openblog.bench", matches = "true")
class SensitiveWordBenchmarkTest {

    private static final int WARMUP = 500;
    private static final int ROUNDS = 1_000;

    @Test
    void compare() throws Exception {
        List<String> words = loadDictionary();
        CharNormalizer normalizer = CharNormalizer.houbbCompatible();
        WordMatcher houbb = HoubbWordMatcher.build(words);
        TrieWordMatcher trie = TrieWordMatcher.build(words, normalizer);
        AcWordMatcher ac = AcWordMatcher.build(words, normalizer);

        System.out.printf("%n词条数=%d, trie nodes=%d, ac nodes=%d%n",
                words.size(), trie.nodeCount(), ac.nodeCount());

        List<Case> cases = List.of(
                new Case("短文本·无命中", repeat("这是一段用于测试性能的正常中文文本，没有敏感内容。", 4)),
                new Case("短文本·有命中", repeat("这是一段正常文本，中间夹着敏感词和广告。", 6)),
                new Case("长文本·无命中", repeat("这是一段用于测试性能的正常中文文本，没有敏感内容。", 200)),
                new Case("长文本·有命中", repeat("这是一段正常文本，中间夹着敏感词和广告。", 200))
        );

        System.out.printf("%-16s %-8s %12s %12s%n", "用例", "引擎", "contains(µs)", "filter(µs)");
        for (Case c : cases) {
            for (WordMatcher m : List.of(houbb, trie, ac)) {
                long containsNs = bench(() -> m.contains(c.text));
                long filterNs = bench(() -> m.filter(c.text));
                System.out.printf("%-16s %-8s %12.1f %12.1f%n",
                        c.name, m.engine(), containsNs / 1000.0, filterNs / 1000.0);
            }
        }
    }

    private static long bench(Runnable task) {
        for (int i = 0; i < WARMUP; i++) {
            task.run();
        }
        long[] samples = new long[ROUNDS];
        for (int i = 0; i < ROUNDS; i++) {
            long t0 = System.nanoTime();
            task.run();
            samples[i] = System.nanoTime() - t0;
        }
        Arrays.sort(samples);
        return samples[ROUNDS / 2];
    }

    private static String repeat(String text, int times) {
        StringBuilder sb = new StringBuilder(text.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(text);
        }
        return sb.toString();
    }

    private static List<String> loadDictionary() throws Exception {
        String limit = System.getProperty("openblog.bench.words", "20000");
        int max = Integer.parseInt(limit);
        List<String> words = new ArrayList<>();
        try (InputStream in = SensitiveWordBenchmarkTest.class.getResourceAsStream("/sensitive_word_dict.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while (words.size() < max && (line = reader.readLine()) != null) {
                String w = line.trim();
                if (!w.isEmpty() && w.length() <= 128) {
                    words.add(w);
                }
            }
        }
        return words;
    }

    private record Case(String name, String text) {
    }
}
