package com.yqz.openblog.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.gateway.config.GatewayProperties;
import com.yqz.openblog.gateway.config.JwtProperties;
import com.yqz.openblog.redis.limiter.SlidingWindowLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private GatewayProperties props;
    private SlidingWindowLimiter limiter;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        props = new GatewayProperties();
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret("test-secret-key-0123456789abcdef-0123456789");
        limiter = mock(SlidingWindowLimiter.class);
        filter = new RateLimitFilter(props, limiter, new JwtVerifier(jwtProps), new ObjectMapper());
    }

    private void addRule(String path, int limit, GatewayProperties.Scope scope) {
        GatewayProperties.Rule rule = new GatewayProperties.Rule();
        rule.setPath(path);
        rule.setWindowMs(60_000);
        rule.setLimit(limit);
        rule.setScope(scope);
        props.getRateLimit().getRules().add(rule);
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header("X-Forwarded-For", "1.2.3.4").build());
    }

    @Test
    void allowedRequest_passes() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        ServerWebExchange ex = exchange("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void overLimit_returns429() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(false);
        MockServerWebExchange ex = exchange("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("\"code\":4290");
    }

    @Test
    void disabled_passesWithoutCallingLimiter() {
        props.getRateLimit().setEnabled(false);
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        ServerWebExchange ex = exchange("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();
        verify(limiter, never()).tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void unmatchedPath_passesWithoutCallingLimiter() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        ServerWebExchange ex = exchange("/api/v1/articles/1");
        filter.filter(ex, c -> Mono.empty()).block();
        verify(limiter, never()).tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }
}
