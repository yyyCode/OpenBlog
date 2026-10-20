package com.yqz.openblog.sensitiveword.runtime;

/** 不可变运行快照：构建完成后只替换引用，读路径无锁。 */
public record SensitiveWordSnapshot(
        long version,
        int wordCount,
        String checksum,
        String engine,
        WordMatcher matcher) {

    public static SensitiveWordSnapshot empty() {
        return new SensitiveWordSnapshot(0, 0, "", "none", EmptyWordMatcher.INSTANCE);
    }
}
