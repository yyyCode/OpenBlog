package com.yqz.openblog.sensitiveword.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AC 自动机：Trie + fail 指针 + output 链，单遍扫描。
 * <p>
 * 构建比 Trie 多一次 BFS，节点多两个引用；匹配 O(n + 命中数)，长文本 / 高命中场景占优。
 */
public final class AcWordMatcher implements WordMatcher {

    private static final class Node {
        final Map<Character, Node> next = new HashMap<>(4);
        Node fail;
        Node output;
        boolean terminal;
        int length;
    }

    private final Node root;
    private final CharNormalizer normalizer;
    private final int nodeCount;

    private AcWordMatcher(Node root, CharNormalizer normalizer, int nodeCount) {
        this.root = root;
        this.normalizer = normalizer;
        this.nodeCount = nodeCount;
    }

    public static AcWordMatcher build(List<String> words, CharNormalizer normalizer) {
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

        Deque<Node> queue = new ArrayDeque<>();
        for (Node child : root.next.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node cur = queue.poll();
            for (Map.Entry<Character, Node> entry : cur.next.entrySet()) {
                char c = entry.getKey();
                Node child = entry.getValue();
                Node f = cur.fail;
                while (f != null && !f.next.containsKey(c)) {
                    f = f.fail;
                }
                child.fail = (f == null) ? root : f.next.get(c);
                if (child.fail == child) {
                    child.fail = root;
                }
                child.output = child.fail.terminal ? child.fail : child.fail.output;
                queue.add(child);
            }
        }
        return new AcWordMatcher(root, normalizer, count);
    }

    @Override
    public String engine() {
        return "ac";
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
        Node node = root;
        for (int i = 0; i < src.length; i++) {
            char c = normalizer.normalize(src[i]);
            while (node != root && !node.next.containsKey(c)) {
                node = node.fail;
            }
            Node next = node.next.get(c);
            node = (next == null) ? root : next;
            if (node.terminal && WordMatcher.acceptsMatch(src, i - node.length + 1, i + 1)) {
                return new HitRange(i - node.length + 1, i + 1);
            }
            for (Node o = node.output; o != null; o = o.output) {
                if (WordMatcher.acceptsMatch(src, i - o.length + 1, i + 1)) {
                    return new HitRange(i - o.length + 1, i + 1);
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

        Node node = root;
        for (int i = 0; i < src.length; i++) {
            char c = normalizer.normalize(src[i]);
            while (node != root && !node.next.containsKey(c)) {
                node = node.fail;
            }
            Node next = node.next.get(c);
            node = (next == null) ? root : next;

            if (node.terminal) {
                addHit(src, candidates, i, node.length);
            }
            for (Node o = node.output; o != null; o = o.output) {
                addHit(src, candidates, i, o.length);
            }
        }
        return WordMatcher.buildResult(src, candidates);
    }

    private static void addHit(char[] src, List<HitRange> candidates, int endInclusive, int length) {
        int start = endInclusive - length + 1;
        if (start < 0 || !WordMatcher.acceptsMatch(src, start, endInclusive + 1)) {
            return;
        }
        candidates.add(new HitRange(start, endInclusive + 1));
    }

    public int nodeCount() {
        return nodeCount;
    }
}
