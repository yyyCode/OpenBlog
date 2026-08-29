package com.yqz.openblog.idempotent.strategy;

public enum IdempotentStrategy {
    /** 命中防重直接抛 BizException */
    CACHE_REJECT,
    /** 命中防重时返回上次执行缓存的相同结果 */
    RETURN_SAME_RESULT
}
