package com.yqz.openblog.idempotent.core;

import java.util.List;

/**
 * 幂等组件 Redis Key 生成。命名沿用 RedisKeys 规范：openblog:{env}:repeat:{用途}:{name}:{key...}
 */
public class RepeatExecuteKeyBuilder {

    private static final String PREFIX = "openblog";
    private static final String LOCK = "repeat:lock";
    private static final String FLAG = "repeat:flag";
    private static final String RESULT = "repeat:result";

    private final String envPrefix;

    public RepeatExecuteKeyBuilder(String envPrefix) {
        this.envPrefix = (envPrefix == null || envPrefix.isBlank()) ? "" : envPrefix.trim();
    }

    private String join(String kind, String name, List<String> keys) {
        StringBuilder sb = new StringBuilder(PREFIX);
        if (!envPrefix.isEmpty()) {
            sb.append(':').append(envPrefix);
        }
        sb.append(':').append(kind).append(':').append(name);
        for (String k : keys) {
            sb.append(':').append(k);
        }
        return sb.toString();
    }

    public String lockKey(String name, List<String> keys) {
        return join(LOCK, name, keys);
    }

    public String flagKey(String name, List<String> keys) {
        return join(FLAG, name, keys);
    }

    public String resultKey(String name, List<String> keys) {
        return join(RESULT, name, keys);
    }
}
