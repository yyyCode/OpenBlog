package com.yqz.openblog.sensitiveword.runtime;

import com.yqz.openblog.config.SensitiveWordProperties;

import java.util.Locale;

/** 引擎覆盖值解析：管理台请求与 Redis 覆盖值共用同一套规则。 */
public final class EngineSelection {

    /** 管理台表示"清除覆盖、跟随 YAML 默认引擎"的取值。 */
    public static final String DEFAULT_TOKEN = "default";

    private EngineSelection() {
    }

    /**
     * 解析管理台请求。
     *
     * @return null 表示跟随配置默认值
     * @throws IllegalArgumentException 引擎名非法，或试图把主引擎切到 NONE
     */
    public static SensitiveWordProperties.Engine parseRequest(String raw) {
        if (raw == null || raw.isBlank() || DEFAULT_TOKEN.equalsIgnoreCase(raw.trim())) {
            return null;
        }
        SensitiveWordProperties.Engine engine = parseName(raw);
        if (engine == null || engine == SensitiveWordProperties.Engine.NONE) {
            throw new IllegalArgumentException("不支持的匹配引擎：" + raw);
        }
        return engine;
    }

    /** 解析 Redis 覆盖值；非法值按"未设置"处理，避免坏数据把节点卡住。 */
    public static SensitiveWordProperties.Engine parseOverride(String raw) {
        try {
            return parseRequest(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 枚举转 Redis / 接口使用的短名（houbb、trie、ac）。 */
    public static String toWire(SensitiveWordProperties.Engine engine) {
        return engine == null ? null : engine.name().toLowerCase(Locale.ROOT);
    }

    private static SensitiveWordProperties.Engine parseName(String raw) {
        try {
            return SensitiveWordProperties.Engine.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
