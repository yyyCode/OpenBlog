package com.yqz.openblog.sensitiveword.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典树匹配器：逐起点从根遍历。
 * <p>
 * 优点：构建最快、节点最少；{@code contains} 可提前返回。
 * 缺点：最坏 O(n × L)，长文本 + 长公共前缀 + 无命中时重复走前缀。
 */
public final class TrieWordMatcher implements WordMatcher {

    private static final class Node {
        final Map<Character, Node> next = new HashMap<>(4);
        boolean terminal;
        int length;
    }

    private final Node root;
    private final CharNormalizer normalizer;
    private final int nodeCount;

    private TrieWordMatcher(Node root, CharNormalizer normalizer, int nodeCount) {
        this.root = root;
        this.normalizer = normalizer;
        this.nodeCount = nodeCount;
    }

    public static TrieWordMatcher build(List<String> words, CharNormalizer normalizer) {
        Node root = new Node();
        int count = 1;
        for (String word : words) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            Node cur = root;
            for (int i = 0; i < word.length(); i++) {
                char c = normalizer.normalize(word.charAt(i));
                Node child = cur.next.get(c);
                if (child == null) {
                    child = new Node();
                    cur.next.put(c, child);
                    count++;
                }
                cur = child;
            }
            cur.terminal = true;
            cur.length = word.length();
        }
        return new TrieWordMatcher(root, normalizer, count);
    }

    @Override
    public String engine() {
        return "trie";
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
        char[] src = text.toCharArray();
        for (int i = 0; i < src.length; i++) {
            Node node = root;
            for (int j = i; j < src.length; j++) {
                node = node.next.get(normalizer.normalize(src[j]));
                if (node == null) {
                    break;
                }
                if (node.terminal && WordMatcher.acceptsMatch(src, i, j + 1)) {
                    return new HitRange(i, j + 1);
                }
            }
        }
        return null;
    }

    @Override
    public FilterResult filter(String text) {
        if (text == null || text.isEmpty()) {
            return FilterResult.empty(text);
        }
        char[] src = text.toCharArray();
        List<HitRange> candidates = new ArrayList<>();

        for (int i = 0; i < src.length; i++) {
            Node node = root;
            for (int j = i; j < src.length; j++) {
                node = node.next.get(normalizer.normalize(src[j]));
                if (node == null) {
                    break;
                }
                if (node.terminal && WordMatcher.acceptsMatch(src, i, j + 1)) {
                    candidates.add(new HitRange(i, j + 1));
                }
            }
        }
        return WordMatcher.buildResult(src, candidates);
    }

    public int nodeCount() {
        return nodeCount;
    }
}
