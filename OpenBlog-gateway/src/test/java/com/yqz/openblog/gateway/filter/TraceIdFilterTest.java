package com.yqz.openblog.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void generatesTraceIdWhenAbsent() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/x").build());
        ServerWebExchange[] forwarded = new ServerWebExchange[1];
        GatewayFilterChain chain = ex -> {
            forwarded[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        String attr = exchange.getAttribute(TraceIdFilter.TRACE_ID_ATTR);
        assertThat(attr).isNotNull().isNotEmpty();
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER))
                .isEqualTo(attr.toString());
        assertThat(forwarded[0].getRequest().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER))
                .isEqualTo(attr.toString());
    }

    @Test
    void propagatesIncomingTraceId() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/x").header("X-Trace-Id", "t-123").build());
        ServerWebExchange[] forwarded = new ServerWebExchange[1];
        GatewayFilterChain chain = ex -> {
            forwarded[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        String attr = exchange.getAttribute(TraceIdFilter.TRACE_ID_ATTR);
        assertThat(attr).isEqualTo("t-123");
        assertThat(forwarded[0].getRequest().getHeaders().getFirst("X-Trace-Id")).isEqualTo("t-123");
    }
}
