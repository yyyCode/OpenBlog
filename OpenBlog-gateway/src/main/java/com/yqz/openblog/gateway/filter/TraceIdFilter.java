package com.yqz.openblog.gateway.filter;

import com.yqz.openblog.common.TraceId;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局过滤器（order=-3，最先执行）：
 * 生成/透传 X-Trace-Id 到 exchange 属性、响应头与下游请求头。
 * WebFlux 下 traceId 载体是 exchange 属性（非 ThreadLocal/MDC），供错误处理器统一带出。
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_ATTR = "openblog.traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String traceId = (incoming != null && !incoming.isBlank()) ? incoming : TraceId.get();
        exchange.getAttributes().put(TRACE_ID_ATTR, traceId);
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        ServerWebExchange mutated = exchange.mutate()
                .request(req -> req.header(TRACE_ID_HEADER, traceId))
                .build();
        return chain.filter(mutated);
    }

    public static String traceIdOf(ServerWebExchange exchange) {
        Object v = exchange.getAttribute(TRACE_ID_ATTR);
        return v != null ? v.toString() : "";
    }

    @Override
    public int getOrder() {
        return -3;
    }
}
