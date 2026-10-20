package com.yqz.openblog.sensitiveword.runtime;

import java.util.List;

/** 不可变运行快照：构建完成后只替换引用，读路径无锁。 */
public record SensitiveWordSnapshot(
        long version,
        int wordCount,
        String checksum,
        String engine,
        List<String> words,
        WordMatcher matcher) {

    public SensitiveWordSnapshot {
        // 保留词表用于热切换引擎重建，避免回读 Redis/MySQL
        words = List.copyOf(words);
    }

    public static SensitiveWordSnapshot empty() {
        return new SensitiveWordSnapshot(0, 0, "", "none", List.of(), EmptyWordMatcher.INSTANCE);
    }
}
