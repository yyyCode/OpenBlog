package com.yqz.openblog.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 安全相关异常处理，仅在 classpath 存在 spring-security 时由 CommonAutoConfiguration 装配。
 * （email 等无 security 依赖的服务自动跳过。）
 */
@RestControllerAdvice
public class SecurityExceptionHandler {

    /**
     * 方法级鉴权（@PreAuthorize）失败会抛出 AuthorizationDeniedException；
     * 以前会落到兜底 Exception -> 5001，导致前端误判为“服务器异常”。
     */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Object>> onAccessDenied(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(4030, "无权限"));
    }
}
