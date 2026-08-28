package com.yqz.openblog.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.gateway.filter.TraceIdFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 统一网关错误响应：ApiResponse 结构 + traceId，与 business 契约一致（前端 http.js 零改动）。
 */
public final class GatewayResponses {

    private GatewayResponses() {
    }

    public static Mono<Void> writeJson(ServerWebExchange exchange, ObjectMapper objectMapper,
                                       HttpStatus status, int code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ApiResponse<Object> resp = ApiResponse.fail(code, message);
        resp.setTraceId(resolveTraceId(exchange));
        String body;
        try {
            body = objectMapper.writeValueAsString(resp);
        } catch (JsonProcessingException e) {
            body = "{\"code\":5000,\"message\":\"系统繁忙\"}";
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static String resolveTraceId(ServerWebExchange exchange) {
        // 规范来源是 TraceIdFilter 写入的 exchange 属性；兜底取请求头（过滤器未执行/早期错误时仍能带出）。
        String traceId = TraceIdFilter.traceIdOf(exchange);
        if (traceId == null || traceId.isBlank()) {
            String header = exchange.getRequest().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
            if (header != null) {
                traceId = header;
            }
        }
        return traceId == null ? "" : traceId;
    }
}
