package com.yqz.openblog.common;

/**
 * 业务异常（统一由 GlobalExceptionHandler 捕获并转成 ApiResponse）。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

