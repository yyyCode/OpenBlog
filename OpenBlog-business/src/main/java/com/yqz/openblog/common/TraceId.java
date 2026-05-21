package com.yqz.openblog.common;

import java.util.UUID;

/**
 * 简单 traceId 生成器（MVP）。
 * 后续可接 MDC + 日志框架对接。
 */
public final class TraceId {

    private TraceId() {
    }

    public static String get() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

