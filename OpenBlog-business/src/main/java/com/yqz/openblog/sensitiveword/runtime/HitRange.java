package com.yqz.openblog.sensitiveword.runtime;

/** 命中区间，左闭右开 {@code [start, endExclusive)}，下标基于原文。 */
public record HitRange(int start, int endExclusive) {

    public int length() {
        return endExclusive - start;
    }
}
