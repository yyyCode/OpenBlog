package com.yqz.openblog.idempotent.handle;

import com.yqz.openblog.idempotent.constant.RepeatExecuteLimitConstant;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用内存标识存储（含简单 TTL 判定）。
 */
public class InMemoryRepeatExecuteFlagStore implements RepeatExecuteFlagStore {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private record Entry(String value, long expireAtMillis) {
    }

    private boolean alive(Entry e) {
        return e != null && e.expireAtMillis() > System.currentTimeMillis();
    }

    @Override
    public boolean isSuccess(String flagKey) {
        Entry e = store.get(flagKey);
        return alive(e) && RepeatExecuteLimitConstant.FLAG_SUCCESS.equals(e.value());
    }

    @Override
    public void setFlag(String flagKey, long ttlSeconds) {
        store.put(flagKey, new Entry(RepeatExecuteLimitConstant.FLAG_SUCCESS,
                System.currentTimeMillis() + ttlSeconds * 1000));
    }

    @Override
    public Optional<String> getResult(String resultKey) {
        Entry e = store.get(resultKey);
        return alive(e) ? Optional.of(e.value()) : Optional.empty();
    }

    @Override
    public void setResult(String resultKey, String json, long ttlSeconds) {
        store.put(resultKey, new Entry(json, System.currentTimeMillis() + ttlSeconds * 1000));
    }
}
