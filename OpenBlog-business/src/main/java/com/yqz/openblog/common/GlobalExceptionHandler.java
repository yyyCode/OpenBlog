package com.yqz.openblog.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;

/**
 * 统一异常处理（MVP）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> onBiz(BizException ex) {
        int code = ex.getCode();
        // MVP：约定 code 的前 3 位近似映射为 HTTP status（如 4041 -> 404）。
        int httpStatus = Math.max(400, Math.min(500, code / 10));
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
     * 方法级鉴权（@PreAuthorize）失败会抛出 AuthorizationDeniedException；
     * 以前会落到兜底 Exception -> 5001，导致前端误判为“服务器异常”。
     */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Object>> onAccessDenied(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(4030, "无权限"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> onOther(Exception ex) {
        log.error("unhandled server error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(5001, "服务器异常"));
    }
}

