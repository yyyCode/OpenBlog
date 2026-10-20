package com.yqz.openblog.sensitiveword.runtime;

/** 空词表匹配器：任何文本都不命中。 */
public final class EmptyWordMatcher implements WordMatcher {

    public static final EmptyWordMatcher INSTANCE = new EmptyWordMatcher();

    private EmptyWordMatcher() {
    }

    @Override
    public FilterResult filter(String text) {
        return FilterResult.empty(text);
    }

    @Override
    public boolean contains(String text) {
        return false;
    }

    @Override
    public HitRange firstHit(String text) {
        return null;
    }

    @Override
    public String engine() {
        return "none";
    }
}
