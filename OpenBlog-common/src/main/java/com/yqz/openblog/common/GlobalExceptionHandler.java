package com.yqz.openblog.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理（OpenBlog-common）。通过 CommonAutoConfiguration 自动装配注册。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> onBiz(BizException ex) {
        int code = ex.getCode();
        // MVP：约定 code 的前 3 位近似映射为 HTTP status（如 4041 -> 404）。
        int httpStatus = Math.max(400, Math.min(500, code / 10));
        // 5xxx 业务码 = 服务端故障，必须留痕（否则前端看到错误页但后端零日志）；
        // 4xxx 属预期业务拒绝，不打日志防刷屏。
        if (code >= 5000) {
            log.error("biz server error: code={}", code, ex);
        }
        return ResponseEntity.status(httpStatus).body(ApiResponse.fail(code, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> onValidation(MethodArgumentNotValidException ex) {
        var fe = ex.getBindingResult().getFieldError();
        String msg =
                fe != null && fe.getDefaultMessage() != null && !fe.getDefaultMessage().isBlank()
                        ? fe.getDefaultMessage()
                        : "参数校验失败";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(4001, msg));
    }

    /**
     * IO 异常（MinIO 读写、文件读写等）直接返回原始错误信息，便于排查。
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Object>> onIO(IOException ex) {
        log.error("IO exception", ex);
        String msg = ex.getMessage() != null ? ex.getMessage() : "IO异常";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(5001, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> onOther(Exception ex) {
        log.error("unhandled server error", ex);
        String msg = ex.getMessage() != null ? ex.getMessage() : "服务器异常";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(5001, msg));
    }
}
