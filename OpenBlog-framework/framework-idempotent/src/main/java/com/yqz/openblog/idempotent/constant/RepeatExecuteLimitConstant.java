package com.yqz.openblog.idempotent.constant;

public final class RepeatExecuteLimitConstant {
    private RepeatExecuteLimitConstant() {
    }

    /** 命中防重时的业务码 */
    public static final int REJECT_CODE = 4299;

    /** 标识写入成功的值 */
    public static final String FLAG_SUCCESS = "success";
}
