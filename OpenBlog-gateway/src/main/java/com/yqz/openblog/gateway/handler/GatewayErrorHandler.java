package com.yqz.openblog.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;

/**
 * 网关内部异常统一兜底（仅网关自身错误；下游业务正常/业务错误响应原样透传）。
 */
@Component
@Order(-1)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status;
        int code;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            int s = rse.getStatusCode().value();
            if (s == HttpStatus.NOT_FOUND.value() || s == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                status = HttpStatus.BAD_GATEWAY;
                code = 5000;
                message = "服务暂不可用";
            } else if (s == HttpStatus.UNAUTHORIZED.value()) {
                status = HttpStatus.UNAUTHORIZED;
                code = 4010;
                message = "登录已失效";
            } else if (s == HttpStatus.TOO_MANY_REQUESTS.value()) {
                status = HttpStatus.TOO_MANY_REQUESTS;
                code = 4290;
                message = "请求过于频繁，请稍后再试";
            } else {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                code = 5000;
                message = "系统繁忙";
            }
        } else if (ex instanceof IOException) {
            // 下游不可达（ConnectException 等）
            status = HttpStatus.BAD_GATEWAY;
            code = 5000;
            message = "服务暂不可用";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = 5000;
            message = "系统繁忙";
        }

        log.error("gateway error: method={} path={}", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(), ex);
        return GatewayResponses.writeJson(exchange, objectMapper, status, code, message);
    }
}
